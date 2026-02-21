// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    id("com.android.application") version "9.0.1" apply false
    id("com.google.devtools.ksp") version "2.3.6" apply false
}

tasks.register<Delete>("clean") {
    delete(layout.buildDirectory)
}
