Jumble
======

An Android service implementation of the Mumble protocol, designed to be used as the backend of Plumble in future releases.

Note that this project will only provide a service and utilities necessary for operation, and will never be a standalone app. That's what Plumble will be for.

Why?
-----

There's no open-source (free as in freedom) Mumble protocol implementation on Android. Jumble will serve to fill this void and aid in bringing a full-featured Mumble client to Android under the Apache License v2.

Due to the restrictive nature of the 'mumble-android' project by @pcgod, no sources or derivatives will be copied from that project.

The primary goal of the Jumble project is to replicate the functionality of the desktop Mumble client as much as possible. At the moment, development is focused on adding complete support for the Mumble protocol, particularly audio support (adding Speex and CELT alpha).

When?
-----

Jumble should be usable soon™. Likely by the end of summer 2013.

How will I be able to use this?
-----

Upon release, Jumble will be usable as an Android library project. You'll be able to interface with the service via AIDL.
