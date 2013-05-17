import javax.sound.sampled.AudioFileFormat.Type;

import com.sun.speech.freetts.Voice;
import com.sun.speech.freetts.VoiceManager;
import com.sun.speech.freetts.audio.AudioPlayer;
import com.sun.speech.freetts.audio.SingleFileAudioPlayer;

public class AudioFileGenerator {

	public static void generateWav(String message) {
		 	VoiceManager voiceManager = VoiceManager.getInstance();
	        Voice helloVoice = voiceManager.getVoice("kevin16");
	        helloVoice.allocate();
	        String filename = SIPSpeaker.getProperties().getProperty("message_wav").replace(".wav", "");
	        AudioPlayer audioPlayer = new SingleFileAudioPlayer(filename, Type.WAVE);
	        helloVoice.setAudioPlayer(audioPlayer);
	        helloVoice.speak(message);
	        helloVoice.deallocate();
	        audioPlayer.close();
	}
	public static void generateWav(String message,String filename) {
	 	VoiceManager voiceManager = VoiceManager.getInstance();
        Voice helloVoice = voiceManager.getVoice("kevin16");
        helloVoice.allocate();
        AudioPlayer audioPlayer = new SingleFileAudioPlayer(filename.replace(".wav",""), Type.WAVE);
        helloVoice.setAudioPlayer(audioPlayer);
        helloVoice.speak(message);
        helloVoice.deallocate();
        audioPlayer.close();
}
}
