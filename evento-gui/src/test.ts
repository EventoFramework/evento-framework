// This file is required by karma.conf.js and loads recursively all the .spec and framework files

import 'zone.js/testing';
// TestBed JIT-compiles spec-declared components at runtime, so the compiler must be
// loaded explicitly now that the app bootstraps via @angular/platform-browser
// (platform-browser-dynamic, which bundled it, is deprecated and removed).
import '@angular/compiler';
import {getTestBed} from '@angular/core/testing';
import {BrowserTestingModule, platformBrowserTesting} from '@angular/platform-browser/testing';

// First, initialize the Angular testing environment.
getTestBed().initTestEnvironment(
  BrowserTestingModule,
  platformBrowserTesting()
);
