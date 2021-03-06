package tarehart.rlbot.planning

import tarehart.rlbot.AgentOutput
import tarehart.rlbot.input.CarData
import tarehart.rlbot.math.VectorUtil
import tarehart.rlbot.math.vector.Vector2
import tarehart.rlbot.steps.blind.BlindSequence
import tarehart.rlbot.steps.blind.BlindStep
import tarehart.rlbot.steps.landing.LandGracefullyStep
import tarehart.rlbot.steps.travel.LineUpInReverseStep
import tarehart.rlbot.time.Duration
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin

object SetPieces {

    fun speedupFlip(): Plan {
        return Plan()
                .unstoppable()
                .withStep(BlindSequence()
                        .withStep(BlindStep(Duration.ofSeconds(.04),
                                AgentOutput()
                                        .withPitch(-0.3)
                                        .withJump(true)
                                        .withThrottle(1.0)))
                        .withStep(BlindStep(Duration.ofSeconds(.02),
                                AgentOutput()
                                        .withPitch(-1.0)
                                        .withThrottle(1.0)
                        ))
                        .withStep(BlindStep(Duration.ofSeconds(.3),
                                AgentOutput()
                                        .withJump(true)
                                        .withThrottle(1.0)
                                        .withPitch(-1.0)))
                        .withStep(BlindStep(Duration.ofSeconds(.5),
                                AgentOutput()
                                        .withThrottle(1.0)
                                        .withPitch(-1.0)
                        )))
                .withStep(LandGracefullyStep(LandGracefullyStep.FACE_MOTION))
    }

    fun halfFlip(waypoint: Vector2): Plan {

        return Plan()
                .unstoppable()
                .withStep(LineUpInReverseStep(waypoint))
                .withStep(BlindStep(Duration.ofSeconds(.05),
                        AgentOutput()
                                .withPitch(1.0)
                                .withJump(true)
                                .withThrottle(-1.0)))
                .withStep(BlindStep(Duration.ofSeconds(.05),
                        AgentOutput()
                                .withPitch(1.0)
                                .withThrottle(-1.0)
                ))
                .withStep(BlindStep(Duration.ofSeconds(.05),
                        AgentOutput()
                                .withJump(true)
                                .withPitch(1.0)))
                .withStep(BlindStep(Duration.ofSeconds(.15),
                        AgentOutput()
                                .withPitch(1.0)))
                .withStep(BlindStep(Duration.ofSeconds(.4),
                        AgentOutput()
                                .withBoost()
                                .withPitch(-1.0)
                ))
                .withStep(BlindStep(Duration.ofSeconds(.5),
                        AgentOutput()
                                .withBoost()
                                .withPitch(-1.0)
                                .withRoll(1.0)
                ))
    }

    fun jumpSuperHigh(howHigh: Double): Plan {
        return Plan()
                .withStep(BlindStep(Duration.ofSeconds(.3), AgentOutput()
                        .withJump(true)
                        .withPitch(1.0)
                ))
                .withStep(BlindStep(Duration.ofSeconds(.05), AgentOutput()
                        .withPitch(1.0)
                        .withBoost(true)
                ))
                .withStep(BlindStep(Duration.ofSeconds(.05), AgentOutput()
                        .withBoost(true)
                        .withJump(true)
                ))
                .withStep(BlindStep(Duration.ofSeconds(howHigh / 10), AgentOutput()
                        .withJump(true)
                        .withBoost(true)
                ))
                .withStep(LandGracefullyStep(LandGracefullyStep.FACE_BALL))
    }

    fun anyDirectionBlindSequence(car: CarData, direction: Vector2): BlindSequence {

        val sequence = BlindSequence()

        val velocityOffset = VectorUtil.project(car.velocity.flatten(), VectorUtil.orthogonal(direction))
        val realDirection = direction.scaledToMagnitude(10.0) - velocityOffset
        val correctionAngle = car.orientation.noseVector.flatten().correctionAngle(realDirection)

        val pitch = -cos(correctionAngle)
        val roll = -sin(correctionAngle)

        sequence
                .withStep(BlindStep(Duration.ofSeconds(.05), AgentOutput().withJump(true)))
                .withStep(BlindStep(Duration.ofSeconds(.05), AgentOutput()))
                .withStep(BlindStep(Duration.ofSeconds(.24),
                        AgentOutput()
                                .withJump(true)
                                .withPitch(pitch)
                                .withRoll(roll)))

        if (pitch > .5) {
            sequence
                    .withStep(BlindStep(Duration.ofSeconds(.4), AgentOutput()
                            .withPitch(-1.0)
                    ))
                    .withStep(BlindStep(Duration.ofSeconds(.3), AgentOutput()
                            .withPitch(-1.0)
                            .withRoll(sign(roll))
                    ))
        }

        return sequence
    }

    fun anyDirectionFlip(car: CarData, direction: Vector2): Plan {
        val sequence = anyDirectionBlindSequence(car, direction);
        return Plan().unstoppable().withStep(sequence).withStep(LandGracefullyStep { direction })
    }
}
