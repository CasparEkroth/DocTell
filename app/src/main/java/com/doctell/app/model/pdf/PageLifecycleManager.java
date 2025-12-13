package com.doctell.app.model.pdf;


import android.util.Log;

import com.doctell.app.model.entity.PageState;

/**
 * PageLifecycleManager
 */
public class PageLifecycleManager {

    private static final String TAG = "PageLifecycleManager";

    private PageState currentState = PageState.IDLE;
    private int currentPageIndex = -1;
    private int sentenceIndex = -1;
    private final Object stateLock = new Object();

    public PageLifecycleManager() {
        this.currentState = PageState.IDLE;
    }

    public boolean canStartPageLoad(int pageIndex) {
        synchronized (stateLock) {
            boolean allowed = (currentState != PageState.LOADING_PAGE);

            if (!allowed) {
                Log.w(TAG, "Cannot start page load: state=" + currentState +
                        ", requested_page=" + pageIndex + ", current_page=" + currentPageIndex);
            }
            return allowed;
        }
    }

    public boolean canProcessChunkStart(int pageIndex, int chunkIndex) {
        synchronized (stateLock) {
            boolean allowed = (currentState == PageState.PAGE_READY ||
                    currentState == PageState.SPEAKING) &&
                    pageIndex == currentPageIndex;

            if (!allowed) {
                Log.e(TAG, "BLOCKED onChunkStart: state=" + currentState +
                        ", page_check=" + (pageIndex == currentPageIndex) +
                        ", current_page=" + currentPageIndex +
                        ", requested_page=" + pageIndex +
                        ", chunk=" + chunkIndex);
            }
            return allowed;
        }
    }

    public boolean isCurrentPage(int pageIndex) {
        synchronized (stateLock) {
            return pageIndex == currentPageIndex;
        }
    }

    public void startPageLoad(int pageIndex) {
        synchronized (stateLock) {
            currentPageIndex = pageIndex;
            currentState = PageState.LOADING_PAGE;
            sentenceIndex = -1;
            Log.d(TAG, "STATE: IDLE/READY → LOADING_PAGE (page=" + pageIndex + ")");
        }
    }

    public void markPageReady(int pageIndex) {
        synchronized (stateLock) {
            if (currentPageIndex == pageIndex) {
                currentState = PageState.PAGE_READY;
                Log.d(TAG, "STATE: LOADING_PAGE → PAGE_READY (page=" + pageIndex + ")");
            }
        }
    }

    public void startSpeakingChunk(int pageIndex, int chunkIndex) {
        synchronized (stateLock) {
            if (currentPageIndex == pageIndex && currentState == PageState.PAGE_READY) {
                currentState = PageState.SPEAKING;
                sentenceIndex = chunkIndex;
                Log.d(TAG, "STATE: PAGE_READY → SPEAKING (page=" + pageIndex +
                        ", chunk=" + chunkIndex + ")");
            }
        }
    }

    public void finishSpeakingChunk(int pageIndex, int chunkIndex) {
        synchronized (stateLock) {
            if (currentPageIndex == pageIndex && currentState == PageState.SPEAKING) {
                currentState = PageState.PAGE_READY;
                sentenceIndex = chunkIndex;
                Log.d(TAG, "STATE: SPEAKING → PAGE_READY (page=" + pageIndex +
                        ", chunk=" + chunkIndex + ")");
            }
        }
    }

    public void finishPage(int pageIndex) {
        synchronized (stateLock) {
            if (currentPageIndex == pageIndex) {
                currentState = PageState.IDLE;
                currentPageIndex = -1;
                sentenceIndex = -1;
                Log.d(TAG, "STATE: PAGE_READY → IDLE (page=" + pageIndex + " finished)");
            }
        }
    }

    public void resetToIdle() {
        synchronized (stateLock) {
            if (currentState != PageState.IDLE) {
                Log.w(TAG, "STATE: " + currentState + " → IDLE (reset due to error)");
                currentState = PageState.IDLE;
                currentPageIndex = -1;
                sentenceIndex = -1;
            }
        }
    }

    public PageState getCurrentState() {
        synchronized (stateLock) {
            return currentState;
        }
    }

    public int getCurrentPageIndex() {
        synchronized (stateLock) {
            return currentPageIndex;
        }
    }

    public int getSentenceIndex() {
        synchronized (stateLock) {
            return sentenceIndex;
        }
    }

    public String getStateString() {
        synchronized (stateLock) {
            return "State=" + currentState +
                    ", Page=" + currentPageIndex +
                    ", Sentence=" + sentenceIndex;
        }
    }
}
