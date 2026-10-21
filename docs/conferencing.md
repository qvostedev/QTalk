# Hosted group calls

QTalk mixes audio on the host computer. With a configured directory service,
temporary room state is exchanged through Apps Script and the `QTalkRooms` tab
in the existing contact spreadsheet. The endpoint is discovered from `QTalkConfig`,
not embedded in the application. No additional settings field is required.

All participants need a compatible QTalk build and the same configured directory.
Clients poll about every three seconds only during a call; slow requests extend
that interval. Only numbers, membership and microphone flags are stored. Speech
animation is local only: the host can see mixer participants speaking and each
guest can see their own microphone activity. Remote speech activity is not sent
through the spreadsheet because its polling is too slow for a reliable indicator.
If a peer does not acknowledge room updates, the host displays a synchronization
notice; the audio call remains usable. Without a configured endpoint, the earlier
in-dialog `application/vnd.qtalk.room` SIP INFO transport remains available, but
requires PBX forwarding and is not reliable on all PBX installations.
Ordinary phones and older clients can participate in audio without a shared view.

The current limit is eight people including the host. Invitations are processed
sequentially using the native mixer's invitation API. The first answered call is
placed in a one-participant mixer before inviting the next person, keeping the
host and existing participants in audio while invitations ring. Answering adds
the invitee to the existing mixer. Declining or timing
out an invitation does not end the other participants' calls. Only the host can
invite or remove participants. Guests hang up only their own SIP leg.
The group overlay uses the same six controls as a regular call. Guest Add is
disabled, and local Hold suspends that user's microphone and playback without
holding the other participants. Unholding restores the prior mute settings.

Closing the host call ends the room. Host migration and crash recovery are not
implemented. Abandoned spreadsheet rooms expire after 45 seconds, with cleanup on
the next request. No history of closed rooms is retained. A network failure is
subject to normal SIP call termination timeouts.

## Temporary deployment limitations

Deploy `employee-presence.gs` and `conference-rooms.gs` in the same Apps Script
project. Run `setupConferenceRooms` once and update the existing web deployment
version, preserving its URL and permissions. See the script README for details.
The script serializes updates with a lock and caps live rooms at 20. The expected
workload is one or two simultaneous small conferences, not large-scale signalling.

The existing anonymous directory endpoint does not authenticate employees. A
random owner token protects room updates through the API (only its hash is stored),
but guest lookup trusts the claimed host and extension. Spreadsheet editors can
alter room state and readers can see participant numbers. This is not secure
proof of SIP identity: use only in the agreed temporary trusted deployment. It
must be replaced by authenticated signalling before wider production use.

## Deployment check

1. Install the same build on the host and at least two guest computers.
2. Start a regular call, use Add, select contacts and invite them.
3. Confirm every QTalk client sees the same roster and guest microphone changes.
4. Add another guest, decline an invitation and remove one participant; the
   remaining calls must stay connected.
5. Check that a guest leaving does not end the room, while the host ending it does.
6. Disconnect Google access temporarily: audio must continue, stale rooms must
   disappear, and the host must not show a successful synchronization for missing peers.
