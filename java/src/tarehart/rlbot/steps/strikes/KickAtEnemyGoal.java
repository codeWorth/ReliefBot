package tarehart.rlbot.steps.strikes;

import mikera.vectorz.Vector2;
import mikera.vectorz.Vector3;
import tarehart.rlbot.AgentInput;
import tarehart.rlbot.input.CarData;
import tarehart.rlbot.math.VectorUtil;
import tarehart.rlbot.planning.GoalUtil;
import tarehart.rlbot.planning.SteerUtil;

public class KickAtEnemyGoal implements KickStrategy {
    @Override
    public Vector3 getKickDirection(AgentInput input) {
        return getKickDirection(input, input.ballPosition);
    }

    @Override
    public Vector3 getKickDirection(AgentInput input, Vector3 ballPosition) {
        CarData car = input.getMyCarData();
        Vector3 toBall = (Vector3) ballPosition.subCopy(car.position);
        return getDirection(car, ballPosition, toBall);
    }

    @Override
    public Vector3 getKickDirection(AgentInput input, Vector3 ballPosition, Vector3 easyKick) {
        return getDirection(input.getMyCarData(), ballPosition, easyKick);
    }

    private Vector3 getDirection(CarData car, Vector3 ballPosition, Vector3 easyKick) {
        Vector2 easyKickFlat = VectorUtil.flatten((Vector3) ballPosition.subCopy(car.position));
        Vector2 toLeftCorner = VectorUtil.flatten((Vector3) GoalUtil.getEnemyGoal(car.team).getLeftPost(6).subCopy(ballPosition));
        Vector2 toRightCorner = VectorUtil.flatten((Vector3) GoalUtil.getEnemyGoal(car.team).getRightPost(6).subCopy(ballPosition));

        double rightCornerCorrection = SteerUtil.getCorrectionAngleRad(easyKickFlat, toRightCorner);
        double leftCornerCorrection = SteerUtil.getCorrectionAngleRad(easyKickFlat, toLeftCorner);
        if (rightCornerCorrection < 0 && leftCornerCorrection > 0) {
            // The easy kick is already on target. Go with the easy kick.
            return new Vector3(easyKickFlat.x, easyKickFlat.y, 0);
        } else if (Math.abs(rightCornerCorrection) < Math.abs(leftCornerCorrection)) {
            return new Vector3(toRightCorner.x, toRightCorner.y, 0);
        } else {
            return new Vector3(toLeftCorner.x, toLeftCorner.y, 0);
        }
    }
}