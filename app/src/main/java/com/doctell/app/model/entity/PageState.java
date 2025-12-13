package com.doctell.app.model.entity;

public enum PageState {
    IDLE,              // No page being rendered
    LOADING_PAGE,      // Page bitmap is being fetched
    PAGE_READY,        // Page bitmap loaded and ready for display
    SPEAKING,          // TTS is speaking (page locked)
    CLOSING_PAGE       // Page is being closed/cleaned up
}

