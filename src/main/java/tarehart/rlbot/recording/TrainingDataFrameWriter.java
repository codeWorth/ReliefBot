package tarehart.rlbot.recording;

import rlbot.flat.*;
import tarehart.rlbot.AgentOutput;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.GZIPOutputStream;

/*
    Outputs in Big Endian!

    Header:
        1 byte w/ number of players
        1 byte w/ index of this player

    All stored as floats (4 bytes each)

    delta time
    ball_position - 3 floats
    ball_velocity - 3 floats

    throttle
    steer
    pitch
    yaw
    roll
    boosting
    jump
    handbrake

    for each player:
        index

        position - 3 floats
        velocity - 3 floats
        rotation - 3 floats [pitch, yaw, roll]
        angular_velocity - 3 floats

        team
        boost_level

            = 9 + 6 + n*14 = 15 + n * 14 floats
            = 60 + n * 56 bytes
 */
public class TrainingDataFrameWriter {

    private static final String pathPrefix = "./training-data/training-data-";

    private static final float fieldXMax = 4096f * 2;
    private static final float fieldYMax = 5120f * 2;
    private static final float fieldZMax = 2044f;

    private static final float playerVMax = 2300f * 2;
    private static final float ballVMax = 6000f * 2;
    private static final float playerAngularVMax = 5.5f * 2;

    private static final int outputBufferSize = 1000000; // flush every 1 mb, make larger for better compression?

    private static TrainingDataFrameWriter _instance = null;

    private DataOutputStream output;
    private boolean streamOpen;
    private BallInfo ballInfo = new BallInfo();
    private Physics physics = new Physics();
    private Vector3 vector3 = new Vector3();
    private PlayerInfo playerInfo = new PlayerInfo();
    private Rotator rotator = new Rotator();

    private TrainingDataFrameWriter(String path, int playerIndex, int playerCount) throws IOException {
        new PrintWriter(path).close(); // clear file, shouldn't be necessary

        // open in append mode
        output = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(new File(path), true), outputBufferSize, true));
        output.writeByte(playerCount); // write number of players
        output.writeByte(playerIndex); // write current player index
        streamOpen = true;
    }

    public static void write(GameTickPacket frame, AgentOutput controls, int playerIndex, float dt) throws IOException {
        if (_instance == null) {
            _instance = new TrainingDataFrameWriter(filePath(playerIndex), playerIndex, frame.playersLength());
        }
        _instance._write(frame, controls, playerIndex, dt);
    }

    public void _write(GameTickPacket frame, AgentOutput controls, int playerIndex, float dt) throws IOException {
        output.writeFloat(dt); // not scaling this shit sorry

        frame.ball(ballInfo).physics(physics).location(vector3);
        output.writeFloat(vector3.x() / fieldXMax + 0.5f);
        output.writeFloat(vector3.y() / fieldYMax + 0.5f);
        output.writeFloat(vector3.z() / fieldZMax);

        physics.velocity(vector3);
        output.writeFloat(vector3.x() / ballVMax + 0.5f);
        output.writeFloat(vector3.y() / ballVMax + 0.5f);
        output.writeFloat(vector3.z() / ballVMax + 0.5f);

        output.writeFloat(controls.getThrottle() / 2f + 0.5f);
        output.writeFloat(controls.getSteer() / 2f + 0.5f);
        output.writeFloat(controls.getPitch() /  2f + 0.5f);
        output.writeFloat(controls.getYaw() / 2f + 0.5f);
        output.writeFloat(controls.getRoll() /  2f + 0.5f);

        output.writeFloat((controls.getBoostDepressed() && frame.players(playerInfo, playerIndex).boost() > 0) ? 1 : 0);
        output.writeFloat(controls.getJumpDepressed() ? 1 : 0);
        output.writeFloat(controls.holdHandbrake() ? 1 : 0);

        for (int i = 0; i < frame.playersLength(); i++) {
            frame.players(playerInfo, i);
            output.writeFloat(i);

            playerInfo.physics(physics).location(vector3);
            output.writeFloat(vector3.x() / fieldXMax + 0.5f);
            output.writeFloat(vector3.y() / fieldYMax + 0.5f);
            output.writeFloat(vector3.z() / fieldZMax);

            physics.velocity(vector3);
            output.writeFloat(vector3.x() / playerVMax + 0.5f);
            output.writeFloat(vector3.y() / playerVMax + 0.5f);
            output.writeFloat(vector3.z() / playerVMax + 0.5f);

            physics.rotation(rotator);
            output.writeFloat(rotator.pitch() / (float)Math.PI + 0.5f);
            output.writeFloat(rotator.yaw() / (float)Math.PI + 0.5f);
            output.writeFloat(rotator.roll() / (float)Math.PI + 0.5f);

            physics.angularVelocity(vector3);
            output.writeFloat(vector3.x() / playerAngularVMax + 0.5f);
            output.writeFloat(vector3.y() / playerAngularVMax + 0.5f);
            output.writeFloat(vector3.z() / playerAngularVMax + 0.5f);

            output.writeFloat(playerInfo.team());
            output.writeFloat(playerInfo.boost());
        }
    }

    public static void close() throws IOException {
        if (_instance == null) return;
        if (!_instance.streamOpen) return;
        _instance.output.close();
        _instance.streamOpen = false;
    }

    private static String filePath(int playerIndex) {
        return pathPrefix + new SimpleDateFormat("yyyyMMddHHmm'-'").format(new Date()) + playerIndex + ".gz";
    }

}
