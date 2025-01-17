buildscript {
  repositories {
    mavenCentral()
    google()
  }
  dependencies {
    classpath("com.android.tools.build:gradle:7.3.1")
    classpath("com.google.gms:google-services:4.3.14")
    classpath("com.squareup.wire:wire-gradle-plugin:4.4.1")
    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.7.20")
  }
}

allprojects {
  repositories {
    mavenCentral()
    maven(url = "https://maven.fabric.io/public")
    google()
  }
}
