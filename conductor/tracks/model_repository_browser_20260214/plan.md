# Plan: Model Repository Browser & Downloader

## Phase 1: Backend API & Model Discovery

### Task 1.1: Create Repository Browser Service [720e783]
- [x] Create domain model for remote model sources
- [x] Define data class `RemoteModelSource` with name, URL, type (wakeword/stopword), language
- [x] Define data class `RemoteModel` with name, version, download URLs, language tag
- [x] Write unit tests for model data classes

### Task 1.2: Implement GitHub API Client [063ac20]
- [x] Create `GitHubModelRepositoryClient` class
- [x] Implement directory listing using GitHub Contents API
- [x] Parse directory structure to extract model names
- [x] Implement version parsing from filenames (extract highest version)
- [x] Generate raw download URLs for .onnx and .tflite files
- [x] Write unit tests with mocked GitHub API responses
- [x] Write integration tests for actual API calls

### Task 1.3: Create Model Download Service
- [ ] Create `ModelDownloadService` class
- [ ] Implement download method with progress tracking
- [ ] Implement file validation (size check)
- [ ] Implement duplicate detection (skip if exists)
- [ ] Handle network errors with retry logic
- [ ] Write unit tests for download service
- [ ] Write tests for error handling and retry logic

### Task 1.4: Conductor - User Manual Verification 'Phase 1'
- [ ] Verify backend components work correctly

---

## Phase 2: UI Components - Source Selection

### Task 2.1: Create Model Repository Browser Window
- [ ] Create new Composable `ModelRepositoryBrowserWindow`
- [ ] Implement window sizing and positioning
- [ ] Add Gruvbox Dark theme styling
- [ ] Write UI tests for window rendering

### Task 2.2: Implement Source Selection Screen
- [ ] Create `SourceSelectionScreen` Composable
- [ ] Display "OpenWakeWord Community - EN" button with icon
- [ ] Add button click handlers
- [ ] Implement loading state while fetching model list
- [ ] Write UI tests for source selection

### Task 2.3: Conductor - User Manual Verification 'Phase 2'
- [ ] Verify source selection UI works correctly

---

## Phase 3: UI Components - Model Selection

### Task 3.1: Create Model List Screen
- [ ] Create `ModelSelectionScreen` Composable
- [ ] Implement scrollable list with Material Design 3
- [ ] Add list header with "Select All" checkbox
- [ ] Display model name with [en] language tag
- [ ] Add individual checkboxes for each model
- [ ] Write UI tests for model list

### Task 3.2: Implement Selection Logic
- [ ] Create ViewModel for model selection
- [ ] Implement "Select All" toggle logic
- [ ] Track selected models state
- [ ] Handle individual selection/deselection
- [ ] Write unit tests for selection logic

### Task 3.3: Add Navigation Between Screens
- [ ] Implement navigation from source selection to model list
- [ ] Add back button to return to source selection
- [ ] Implement cancel button functionality
- [ ] Write UI tests for navigation flow

### Task 3.4: Conductor - User Manual Verification 'Phase 3'
- [ ] Verify model selection UI works correctly

---

## Phase 4: UI Components - Download & Integration

### Task 4.1: Implement Download Progress UI
- [ ] Create `DownloadProgressScreen` Composable
- [ ] Display progress for each downloading model
- [ ] Show success/failure indicators
- [ ] Add "Done" button to close window when complete
- [ ] Write UI tests for download progress

### Task 4.2: Integrate with Settings UI
- [ ] Add "Browse Model Repositories" button to Advanced Settings
- [ ] Position button near existing wakeword settings
- [ ] Wire up button click to open repository browser
- [ ] Refresh wakeword list after downloads complete
- [ ] Write UI tests for integration

### Task 4.3: Implement Language Tag Display
- [ ] Update wakeword dropdown to display "[en]" suffix
- [ ] Ensure downloaded models appear with language tag
- [ ] Write UI tests for language tag display

### Task 4.4: Conductor - User Manual Verification 'Phase 4'
- [ ] Verify complete download flow works correctly

---

## Phase 5: Polish & Error Handling

### Task 5.1: Add Error Handling UI
- [ ] Create error dialog for network failures
- [ ] Display retry option for failed downloads
- [ ] Add "Skip" option for individual failed models
- [ ] Write UI tests for error scenarios

### Task 5.2: Add Loading States
- [ ] Implement skeleton loading UI while fetching model list
- [ ] Add shimmer effect for better UX
- [ ] Disable buttons during loading
- [ ] Write UI tests for loading states

### Task 5.3: Final Integration Testing
- [ ] Test complete flow end-to-end
- [ ] Verify downloaded models work correctly
- [ ] Test cancellation at various points
- [ ] Test duplicate download prevention

### Task 5.4: Conductor - User Manual Verification 'Phase 5'
- [ ] Verify all features work together correctly

---

## Phase 6: Documentation & Cleanup

### Task 6.1: Add Documentation
- [ ] Write KDoc for all new public APIs
- [ ] Add inline comments for complex logic
- [ ] Update README with new feature description

### Task 6.2: Final Review & Cleanup
- [ ] Review code for Clean Architecture compliance
- [ ] Ensure >80% test coverage
- [ ] Run linting and fix any issues
- [ ] Conductor - User Manual Verification 'Phase 6'

### Task 6.3: Conductor - User Manual Verification 'Phase 6'
- [ ] Final verification that all acceptance criteria are met
