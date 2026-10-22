# Hosted group calls

QTalk mixes audio on the host computer. With a configured directory service,
temporary room state is exchanged through Apps Script and the `QTalkRooms` tab
in the existing contact spreadsheet. The endpoint is discovered from `QTalkConfig`,
not embedded in the application. No additional settings field is required.

All participants need a compatible QTalk build and the same configured directory.
The directory bootstrap is polled about every three seconds during a call until
a direct LAN connection is established. The host advertises a temporary private
IPv4 address, random TCP port and per-room capability in the room snapshot.
Guest mute states then update over bounded TCP messages about every 120 ms,
independently of Google requests. Speech is detected from the host's separate
incoming audio levels (threshold -45 dB, 300 ms release) and shared with guests,
whose received audio is already mixed. Each client updates its own mute/speech
indicator locally; silence is never interpreted as a muted microphone.
Only numbers, room state and a
temporary capability are stored; no audio or SIP credentials are sent to Google.
Windows may require permission for QTalk on the private network. The application
does not create firewall exceptions. The host chooses the interface used to reach
the SIP server, with a private-interface fallback. Only private IPv4 destinations
and ephemeral ports are accepted; public, loopback and link-local addresses are rejected.
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

When the host hangs up or quits normally, connected updated guests acknowledge
a two-phase handoff and a successor redials the remaining participants. A short
audio interruption is expected. The replacement incoming call is automatically
accepted only from the agreed successor during a bounded 20-second transition.
Without acknowledgement from every connected guest the host exit is refused and
the audio continues. Unexpected process crashes and power loss are not recovered.
When only two people remain, room synchronization ends and the ordinary call UI
returns. The final native mixer leg is retained internally because removing it
terminates the SIP call in this SDK. Abandoned spreadsheet rooms expire after 45 seconds, with cleanup on
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
must be replaced by authenticated signalling before wider production use. The LAN
channel shares this trusted-directory model, uses a per-room bearer capability,
and is not TLS-encrypted. It must be used only on the agreed trusted private LAN,
not forwarded to the Internet. A reader of the active room sheet can obtain its
capability; the capability is not a substitute for employee identity verification.

## Deployment check

1. Install the same build on the host and at least two guest computers.
2. Start a regular call, use Add, select contacts and invite them.
3. Confirm every QTalk client sees the same roster and guest microphone changes.
4. Add another guest, decline an invitation and remove one participant; the
   remaining calls must stay connected.
5. Check that a guest leaving does not end the room, while the host ending it does.
6. Disconnect Google access temporarily: audio must continue, stale rooms must
   disappear, and the host must not show a successful synchronization for missing peers.
