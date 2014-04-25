WazeBTRouter
============

WazedBTRouter is an Xposed module for Android that attempts to route waze audio notifications through BT mono temporarily.
The main goal of the mod is to interrupt your car's audio (regardless of what you're listening to - radio, etc) whenever Waze has something to say.
This is SUPER EARLY. I've only tested it against an Audi RNS-E bluetooth car stereo, and a google glass... 
I'll probably break if you get a phone call and are using waze.

How it works
============
The mod works by intercepting `com.waze.WazeAudioPlayer` threads. When the thread starts (via `run()`) the module re-routes
audio to bluetooth SCO. 
To prevent the sound from being played before a bluetooth connection is made, the module forces the thread to wait until
a BroadcastReceiver is notified that the audio state has changed to `AudioManager.SCO_AUDIO_STATE_CONNECTED`.
Every time a sound completes, a timer is invoked. If the timer is not cancelled (it is reset every time a new sound arrives) 
then audio is reset back to normal -- i've got a feeling that the logic for this isn't quite right yet.

I welcome any patches or feedback.
