// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply true
  alias(libs.plugins.kotlin.compose) apply true
  alias(libs.plugins.google.devtools.ksp) apply true
  alias(libs.plugins.roborazzi) apply true
  alias(libs.plugins.secrets) apply true
}
