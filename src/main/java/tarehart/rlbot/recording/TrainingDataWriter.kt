package tarehart.rlbot.recording

import rlbot.flat.*
import tarehart.rlbot.AgentOutput
import tarehart.rlbot.tuning.BotLog
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.GZIPOutputStream

class TrainingDataWriter(playerIndex: Int, playerCount: Int) {
    private val output: DataOutputStream
    private var streamOpen: Boolean
    private val ballInfo = BallInfo()
    private val physics = Physics()
    private val vector3 = Vector3()
    private val playerInfo = PlayerInfo()
    private val rotator = Rotator()

    init {
        val path = filePath(playerIndex)
        PrintWriter(path).close() // clear file, shouldn't be necessary
        // open in append mode
        output = DataOutputStream(GZIPOutputStream(FileOutputStream(File(path), true), outputBufferSize, true))
        output.writeFloat(playerCount.toFloat()) // write number of players
        output.writeFloat(playerIndex.toFloat()) // write current player index
        streamOpen = true
    }

    @Throws(IOException::class)
    fun write(frame: GameTickPacket, controls: AgentOutput, playerIndex: Int, dt: Float) {
        if (!streamOpen) return

        output.writeFloat(dt) // not scaling this shit sorry

        frame.ball(ballInfo).physics(physics).location(vector3)
        output.writeFloat(vector3.x() / fieldXMax + 0.5f)
        output.writeFloat(vector3.y() / fieldYMax + 0.5f)
        output.writeFloat(vector3.z() / fieldZMax)

        physics.velocity(vector3)
        output.writeFloat(vector3.x() / ballVMax + 0.5f)
        output.writeFloat(vector3.y() / ballVMax + 0.5f)
        output.writeFloat(vector3.z() / ballVMax + 0.5f)

        output.writeFloat(controls.throttle / 2f + 0.5f)
        output.writeFloat(controls.steer / 2f + 0.5f)
        output.writeFloat(controls.pitch / 2f + 0.5f)
        output.writeFloat(controls.yaw / 2f + 0.5f)
        output.writeFloat(controls.roll / 2f + 0.5f)

        output.writeFloat(if (controls.boostDepressed && frame.players(playerInfo, playerIndex).boost() > 0) 1f else 0f)
        output.writeFloat(if (controls.jumpDepressed) 1f else 0f)
        output.writeFloat(if (controls.holdHandbrake()) 1f else 0f)

        for (i in 0 until frame.playersLength()) {
            frame.players(playerInfo, i)
            output.writeFloat(i.toFloat())

            playerInfo.physics(physics).location(vector3)
            output.writeFloat(vector3.x() / fieldXMax + 0.5f)
            output.writeFloat(vector3.y() / fieldYMax + 0.5f)
            output.writeFloat(vector3.z() / fieldZMax)

            physics.velocity(vector3)
            output.writeFloat(vector3.x() / playerVMax + 0.5f)
            output.writeFloat(vector3.y() / playerVMax + 0.5f)
            output.writeFloat(vector3.z() / playerVMax + 0.5f)

            physics.rotation(rotator)
            output.writeFloat(rotator.pitch() / PI + 0.5f)
            output.writeFloat(rotator.yaw() / PI2 + 0.5f)
            output.writeFloat(rotator.roll() / PI2 + 0.5f)

            physics.angularVelocity(vector3)
            output.writeFloat(vector3.x() / playerAngularVMax + 0.5f)
            output.writeFloat(vector3.y() / playerAngularVMax + 0.5f)
            output.writeFloat(vector3.z() / playerAngularVMax + 0.5f)

            output.writeFloat(playerInfo.team().toFloat())
            output.writeFloat(playerInfo.boost().toFloat() / 100f)
        }
    }

    @Throws(IOException::class)
    fun close() {
        if (!streamOpen) return
        output.close()
        streamOpen = false
    }

    companion object {
        private const val pathPrefix = "./training-data/training-data-"
        private const val fieldXMax: Float = 4096f * 2
        private const val fieldYMax: Float = 5120f * 2
        private const val fieldZMax: Float = 2044f
        private const val playerVMax: Float = 2300f * 2
        private const val ballVMax: Float = 6000f * 2
        private const val playerAngularVMax: Float = 5.5f * 2
        private const val PI: Float = Math.PI.toFloat()
        private const val PI2: Float = Math.PI.toFloat() * 2

        private const val outputBufferSize = 1000000 // flush every 1 mb, make larger for better compression?

        private fun filePath(playerIndex: Int): String {
            return pathPrefix + SimpleDateFormat("yyyy'-'MM'-'dd'-'HH'-'mm'-'").format(Date()) + playerIndex + ".gz"
        }
    }
}