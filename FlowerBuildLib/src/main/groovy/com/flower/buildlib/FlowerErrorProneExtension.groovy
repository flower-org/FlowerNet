package com.flower.buildlib

import org.gradle.api.provider.ListProperty

abstract class FlowerErrorProneExtension {
    abstract ListProperty<String> getAnnotatedPackages()
}
