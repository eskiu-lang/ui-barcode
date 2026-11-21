# Changelog

All notable changes to barcode are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

- `barcode_supported()`: whether a scanner can read codes here, before one is mounted. False
  on a device without a camera (a simulator), in a browser without `BarcodeDetector` and on
  the desktop. The README's platform table says what each platform does.
