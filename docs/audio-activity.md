# Conference activity indicators

Activity is calculated independently for each received call at the host. The host microphone uses conference.inputVolume, rather than a call recording level that may contain mixed conference audio. Existing room synchronization distributes the host activity state.

SpeechActivityDetector samples every 50 ms. It calibrates for 400 ms, estimates background from the low percentile of a rolling 3-second level window, and requires a rise of 8 dB above that background for at least 60 ms. While active it uses a 4.5 dB release threshold and holds the indicator for 240 ms through short pauses. The absolute minimum threshold is -48 dBm0. Mute, invalid readings, removed calls and stale sampling reset activity.

This is an adaptive energy gate, not a speech recognition model. Continuous loud music, changing noise, quiet speech near the background and sustained unmodulated sounds remain difficult to distinguish from levels alone. Real microphones and echo paths need field validation; synthetic tests do not establish Discord-equivalent detection.

## Validation

SpeechActivityDetectorTest covers background levels, comfort noise, onset/release, short spikes, independent inputs, mute and invalid/stale readings. NativeSpeechActivityTest sends generated WAV noise and separate modulated bursts over real loopback SIP/RTP through the native conference mixer. Its background exceeds the previous -35 dBm0 threshold, but must not light either participant; each burst must light its own input and settle back to silence. No external SIP account is required.

Run in the Nix development shell: ./gradlew :desktopApp:build :shared:jvmTest.
