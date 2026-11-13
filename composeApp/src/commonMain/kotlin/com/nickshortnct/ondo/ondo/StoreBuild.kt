package com.nickshortnct.ondo.ondo

/**
 * Whether this build can find, request and download music from outside the library
 * (Lidarr, Soulseek/SoulSync, YouTube, Squid, JioSaavn) and run FLAC upgrades.
 * Off on iOS: App Store guideline 5.2.3 rejects apps that download media from
 * third-party sources, so the iPhone app only plays what is already in Navidrome.
 */
expect val mediaAcquisitionEnabled: Boolean
