This was an assignment at KTH and the goal of this assignment was to create an application which is capable of answering VoIP calls and playing back an audio message to the caller. This involved implementing a SIP speaker which listens on a port for SIP messages, negotiates media settings via SDP and streams an audio message to the caller via RTP/RTCP. It also includes a web server to support the configuration of
the audio message. It includes multi-user support (both on the SIP Speaker and the web server) and RTP audio streaming of a dynamically-generated sound file (using text-to-speech).

This SIP speaker was tested using the SJPhone client (http://www.sjlabs.com/sjp.html). If you wish to use SJPhone on Linux with PulseAudio, then use the following command to start the client:
padsp ./sjphone
This uses PulseAudio user-mode OSS3 sound system emulation. 

- There are three ways to configure the SIP Speaker. They are, in decreasing priority order, command-line arguments, a user-specified configuration file, and a default configuration file. The 	configuration settings are represented in java.util.Properties format. They are handled as follows: first, upon loading the SIP Speaker, it automatically configures a set of  hard coded default 		values. Next, it checks if the user has specified a configuration file and if so, it overrides the default settings with any settings present in the file. If an invalid configuration file is 	specified, it falls back to the hard coded properties. If the user did not specify a configuration file, then it checks if a file exists with the name “default.properties.” If it does, it loads the properties from that file. While loading the properties from the files, it ignores any blank properties. Finally, it processes command-line arguments and replace previous settings with any that are present. 

- The SIP Speaker listens on a configured port for SIP packets (UDP). For each packet, it determines if we have seen a SIP packet from the sender before. If not, it creates a new thread to handle the SIP session. If it already has an active session with the client, it forwards the message to the existing session thread for processing. The thread inspects the message and determines which SIP message type it is. If the message is 
	(a) INVITE, it responds to the caller with a TRYING message, followed by a RINGING message. To allow the caller's phone to ring briefly, it waits 5 seconds and then sends an OK message. It inspects the SDP media formats in the INVITE message body and responds with a subset of these in the OK message body. The 5-second pause is handled by a new thread, so the main thread for the session remains active in case the caller hangs up during this time by sending a CANCEL message.

	(b) ACK in response to an OK message, the SIP session is fully established and the SIP Speaker can begin to transfer audio to the caller. After the audio message has finished streaming to the caller, the SIP Speaker sends a BYE to indicate that it wishes to hang up. While the audio message is streaming to the caller, the main session thread remains active in case the client hangs up before it does. 
		
	(c) OK, it checks to see if it was expecting an OK from the client. The only case where it expects an OK is immediately after sending a BYE. If this is the case, the call has been 	successfully torn down and it discards the session thread. 
			
	(d) CANCEL, it responds with an OK, followed by a 487 Request Terminated message. These messages indicate to the caller that the INVITE request has been canceled, and the 		session ends.

	(e) BYE, it responds with an OK. In this case, the caller has hung up during an active session, and the OK response completes the SIP session tear-down process.

	(f) Other, the request is ignored.

		

- This SIP Speaker uses the FreeTTS library for text-to-speech conversion. The WAV file we produce is the audio data which is streamed to the caller. When the application starts, we dynamically generate a default message. If, the user has provided a configuration file containing another message it dynamically generates this message as well.

- This applications utilizes the Java Media Framework (JMF) to stream the audio message to the caller via RTP/RTCP. Using JMF, it initiates an RTP session with the caller on the negotiated port (plus an RTCP session on an adjacent port), encodes the audio file in the negotiated format, and begins to stream the data to the caller. 

- The multi-threaded web server allows only GET requests. It can serve just one file- form.html. This file displays the current message and contains a text box where a user can enter a new message. When the user submits a new message, it becomes the current message unless the submitted message is blank, in which case the current message is deleted. It utilizes a re-entrant read-write lock for modifications of the message. When a call is received, the handling thread acquires a read lock, which it holds for the duration of the call. In order to change the message, a write lock must be acquired. A write lock may only be acquired when no other threads currently hold a read lock. As such, it blocks an attempt to obtain a write lock until all existing read locks have been released (i.e., all calls have been completed). The result of this is that no modifications to the message will interfere with active calls, because the write lock will not be acquired until all calls have terminated. 


How to compile
-------------------------------------------------------------------------------
Linux:

export CLASSPATH=$CLASSPATH:lib/*:bin

javac src/*.java -d bin


Windows:

set CLASSPATH=%CLASSPATH%;lib/*;bin

javac src/*.java -d bin

How to configure
-------------------------------------------------------------------------------
No additional configuration is necessary, unless you wish to create a custom
configuration file (see default.properties for an example).


How to run
-------------------------------------------------------------------------------
java SIPSpeaker [-c config_file_name] [-user sip_uri] [-http http_bind_address]


How to use
-------------------------------------------------------------------------------
To configure the current message, point a web browser to the configured HTTP
interface and port (e.g., http://127.0.0.1:8080).

To establish a VoIP call, point a soft phone to the configured SIP user,
interface and port (e.g., robot@127.0.0.1:5060).


Known issues
-------------------------------------------------------------------------------
On Windows, there are no known issues. However:

JMF contains a bug in its mechanism for creating sockets in Linux. When 
creating a socket, JMF attempts to check if any interfaces on the system are
assigned the IP address corresponding to the new socket. However, this check
incorrectly fails unless the interface is also assigned the host name of
the system.

If RTP traffic is not coming through and you see an error message from JMF in
the console, try the following:

1. Look up the value in /etc/hostname (cat /etc/hostname)

2. Edit /etc/hosts (sudo emacs /etc/hosts)

3. Create a new entry for the interface being used and assign it the value from
/etc/hosts (e.g., If the /etc/hostname is xyz and the interface uses is 127.0.0.2 then in /etc/hosts add a entry for 127.0.0.2   xyz)

This should resolve the issue, but if it absolutely cannot be avoided, the bug does not occur in Windows.
