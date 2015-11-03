![Travis Build Status](https://travis-ci.org/davepkennedy/bark.svg?branch=master)
# bark 
A bark is a kind of boat. A raft is a kind of boat. Bark is a very simple implementation of Raft. 

# Election
Nothing is currently configurable (since I'm just fiddling with things) so all that can be done is generate a bunch of actors and get them to elect a leader.
This works fairly well as long as the group isn't large, so keep it to 7 or lower.

Eventually the leader will give up the title. There's a 1% chance of this happening, but that doesn't stop it happening immediately…
