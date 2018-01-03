package tarehart.rlbot.intercept;

import tarehart.rlbot.carpredict.AccelerationModel;
import tarehart.rlbot.input.CarData;
import tarehart.rlbot.math.BallSlice;
import tarehart.rlbot.math.DistanceTimeSpeed;
import tarehart.rlbot.math.SpaceTime;
import tarehart.rlbot.math.VectorUtil;
import tarehart.rlbot.math.vector.Vector3;
import tarehart.rlbot.physics.BallPath;
import tarehart.rlbot.physics.DistancePlot;
import tarehart.rlbot.steps.strikes.InterceptStep;
import tarehart.rlbot.steps.strikes.MidairStrikeStep;
import tarehart.rlbot.time.Duration;
import tarehart.rlbot.time.GameTime;

import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;

public class InterceptCalculator {

    public static Optional<Intercept> getInterceptOpportunityAssumingMaxAccel(CarData carData, BallPath ballPath, double boostBudget) {
        DistancePlot plot = AccelerationModel.INSTANCE.simulateAcceleration(carData, Duration.Companion.ofSeconds(4), boostBudget);

        return getInterceptOpportunity(carData, ballPath, plot);
    }

    public static Optional<Intercept> getInterceptOpportunity(CarData carData, BallPath ballPath, DistancePlot acceleration) {
        return getFilteredInterceptOpportunity(carData, ballPath, acceleration, new Vector3(), (a, b) -> true);
    }

    public static Optional<Intercept> getFilteredInterceptOpportunity(
            CarData carData, BallPath ballPath, DistancePlot acceleration, Vector3 interceptModifier, BiPredicate<CarData, SpaceTime> predicate) {
        return getFilteredInterceptOpportunity(carData, ballPath, acceleration, interceptModifier, predicate, (space) -> new StrikeProfile());
    }

    public static Optional<Intercept> getFilteredInterceptOpportunity(
            CarData carData,
            BallPath ballPath,
            DistancePlot acceleration,
            Vector3 interceptModifier,
            BiPredicate<CarData, SpaceTime> predicate,
            Function<Vector3, StrikeProfile> strikeProfileFn) {

        Vector3 groundNormal = new Vector3(0, 0, 1);
        return getFilteredInterceptOpportunity(carData, ballPath, acceleration, interceptModifier, predicate, strikeProfileFn, groundNormal);
    }

    /**
     *
     * @param carData
     * @param ballPath
     * @param acceleration
     * @param interceptModifier an offset from the ball position that the car is trying to reach
     * @param predicate determines whether a particular ball slice is eligible for intercept
     * @param strikeProfileFn a description of how the car will move during the final moments of the intercept
     * @param planeNormal the normal of the plane that the car is driving on for this intercept.
     * @return
     */
    public static Optional<Intercept> getFilteredInterceptOpportunity(
            CarData carData,
            BallPath ballPath,
            DistancePlot acceleration,
            Vector3 interceptModifier,
            BiPredicate<CarData, SpaceTime> predicate,
            Function<Vector3, StrikeProfile> strikeProfileFn,
            Vector3 planeNormal) {

        Vector3 myPosition = carData.getPosition();
        GameTime firstMomentInRange = null;
        double previousOvershoot = 0;

        //for (BallSlice ballMoment: ballPath.getSlices()) {
        for (int i = 0; i < ballPath.getSlices().size(); i++) {
            BallSlice ballMoment = ballPath.getSlices().get(i);
            SpaceTime spaceTime = new SpaceTime(ballMoment.getSpace().plus(interceptModifier), ballMoment.getTime());
            StrikeProfile strikeProfile = strikeProfileFn.apply(spaceTime.getSpace());
            Optional<DistanceTimeSpeed> motionAt = acceleration.getMotionAfterDuration(carData, spaceTime.getSpace(), Duration.Companion.between(carData.getTime(), spaceTime.getTime()), strikeProfile);
            if (motionAt.isPresent()) {
                DistanceTimeSpeed dts = motionAt.get();
                double interceptDistance = VectorUtil.INSTANCE.flatDistance(myPosition, spaceTime.getSpace(), planeNormal);
                double overshoot = dts.getDistance() - interceptDistance;
                if (overshoot > 0) {
                    if (firstMomentInRange == null) {
                        firstMomentInRange = spaceTime.getTime();
                    }
                    if (predicate.test(carData, spaceTime)) {
                        double tweenPoint = 1;
                        if (previousOvershoot < 0) {
                            tweenPoint = (0 - previousOvershoot) / (overshoot - previousOvershoot);
                        }
                        GameTime previousSliceTime = ballPath.getSlices().get(i - 1).getTime();
                        double sliceSeconds = Duration.Companion.between(previousSliceTime, ballMoment.getTime()).getSeconds();
                        GameTime moment = previousSliceTime.plusSeconds(sliceSeconds * tweenPoint);
                        BallSlice realBallMoment = ballPath.getMotionAt(moment).orElse(ballMoment);
                        double boostNeeded = spaceTime.getSpace().getZ() > AirTouchPlanner.NEEDS_AERIAL_THRESHOLD ? AirTouchPlanner.BOOST_NEEDED_FOR_AERIAL : 0;
                        Duration spareTime = Duration.Companion.between(firstMomentInRange, moment);
                        if (spareTime.getMillis() < 0) {
                            spareTime = Duration.Companion.ofMillis(0);
                        }
                        return Optional.of(new Intercept(realBallMoment.getSpace().plus(interceptModifier), realBallMoment.getTime(), boostNeeded, strikeProfile, acceleration, spareTime));
                    }
                }
                previousOvershoot = overshoot;

            } else {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     *
     * @param carData
     * @param ballPath
     * @param interceptModifier an offset from the ball position that the car is trying to reach
     * @return
     */
    public static Optional<SpaceTime> getAerialIntercept(
            CarData carData,
            BallPath ballPath,
            Vector3 interceptModifier,
            GameTime launchMoment) {

        Vector3 myPosition = carData.getPosition();

        for (BallSlice ballMoment: ballPath.getSlices()) {
            SpaceTime intercept = new SpaceTime(ballMoment.getSpace().plus(interceptModifier), ballMoment.getTime());

            Duration timeSinceLaunch = Duration.Companion.between(launchMoment, carData.getTime());
            Duration duration = Duration.Companion.between(carData.getTime(), ballMoment.getTime());
            double zComponent = MidairStrikeStep.getDesiredZComponentBasedOnAccel(intercept.getSpace().getZ(), duration, timeSinceLaunch, carData);
            double desiredNoseAngle = Math.asin(zComponent);
            double currentNoseAngle = Math.asin(carData.getOrientation().getNoseVector().getZ());
            double currentAngleFactor = Math.min(1, 1 / duration.getSeconds());
            double averageNoseAngle = currentNoseAngle * currentAngleFactor + desiredNoseAngle * (1 - currentAngleFactor);

            DistancePlot acceleration = AccelerationModel.INSTANCE.simulateAirAcceleration(carData, duration, Math.cos(averageNoseAngle));
            StrikeProfile strikeProfile = duration.compareTo(MidairStrikeStep.MAX_TIME_FOR_AIR_DODGE) < 0 && averageNoseAngle < .5 ?
                    new StrikeProfile(0, 10, .15, StrikeProfile.Style.AERIAL) :
                    InterceptStep.AERIAL_STRIKE_PROFILE;

            Optional<DistanceTimeSpeed> motionAt = acceleration.getMotionAfterDuration(
                    carData, intercept.getSpace(), Duration.Companion.between(carData.getTime(), intercept.getTime()), strikeProfile);

            if (motionAt.isPresent()) {
                DistanceTimeSpeed dts = motionAt.get();
                double interceptDistance = VectorUtil.INSTANCE.flatDistance(myPosition, intercept.getSpace());
                if (dts.getDistance() > interceptDistance) {
                    return Optional.of(intercept);
                }
            } else {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
