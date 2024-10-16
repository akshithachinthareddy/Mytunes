package mytunes;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Header;
import javazoom.jl.player.advanced.AdvancedPlayer;

import javax.sound.sampled.*;
import java.io.FileInputStream;
import java.io.IOException;

public class MP3Player {
    private AdvancedPlayer player;
    private Thread playbackThread;
    private int playedFrames;
    private boolean isPaused;
    private String filePath;
    private SourceDataLine dataLine;

    public void play(String filePath) {
        this.filePath = filePath;
        playedFrames = 0;
        isPaused = false;
        startPlayback(playedFrames);
    }

    public void stop() {
        if (player != null) {
            player.close();
        }
        playedFrames = 0;
        isPaused = false;
    }

    public void pause() {
        if (player != null && !isPaused) {
            isPaused = true;
            player.close();
        }
    }

    public void unpause() {
        if (isPaused) {
            isPaused = false;
            startPlayback(playedFrames);
        }
    }

    private void startPlayback(int startFrame) {
        playbackThread = new Thread(() -> {
            try (FileInputStream fis = new FileInputStream(filePath)) {
                setupAudioLine(); // Setup the audio line for volume control
                player = new AdvancedPlayer(fis);
                player.play(startFrame, Integer.MAX_VALUE); // Play from startFrame to the end
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        playbackThread.start();
    }

    public void setVolume(int volume) {
        if (dataLine != null) {
            FloatControl volumeControl = (FloatControl) dataLine.getControl(FloatControl.Type.MASTER_GAIN);
            float min = volumeControl.getMinimum();
            float max = volumeControl.getMaximum();
            float gain = (max - min) * (volume / 100.0f) + min;
            volumeControl.setValue(gain);
        }
    }

    private void setupAudioLine() throws LineUnavailableException {
        AudioFormat format = new AudioFormat(44100, 16, 2, true, false);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        dataLine = (SourceDataLine) AudioSystem.getLine(info);
        dataLine.open(format);
        dataLine.start();
    }

    public int getSongDuration(String filePath) {
        try (FileInputStream fis = new FileInputStream(filePath)) {
            Bitstream bitstream = new Bitstream(fis);
            Header header;
            int totalFrames = 0;
            float msPerFrame = 0;

            while ((header = bitstream.readFrame()) != null) {
                totalFrames++;
                msPerFrame = header.ms_per_frame();
                bitstream.closeFrame();
            }

            int totalMs = (int) (totalFrames * msPerFrame);
            return totalMs / 1000;
        } catch (BitstreamException | IOException e) {
            e.printStackTrace();
        }
        return 0;
    }
}
