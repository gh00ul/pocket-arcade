package com.pocketarcade.godot

import androidx.core.content.FileProvider

/**
 * build-13's photo FileProvider (authority "<package>.photos", folder files/photos). Its own class,
 * because Godot's library already declares androidx's FileProvider for its own authority and the
 * manifest merger allows one element per provider class.
 */
class PhotoProvider : FileProvider()
