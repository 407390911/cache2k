package org.cache2k.core;

/*
 * #%L
 * cache2k core
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.cache2k.configuration.Cache2kConfiguration;
import org.cache2k.core.threading.Job;

/**
 * @author Jens Wilke
 */
@SuppressWarnings({"WeakerAccess", "SynchronizationOnLocalVariableOrMethodParameter"})
public abstract class AbstractEviction implements Eviction {

  protected final long maxSize;
  protected final HeapCache heapCache;
  private final Object lock = new Object();
  private long newEntryCounter;
  private long removedCnt;
  private long expiredRemovedCnt;
  private long virginRemovedCnt;
  private long evictedCount;
  private final HeapCacheListener listener;
  private final boolean noListenerCall;
  private Entry[] evictChunkReuse;
  private int chunkSize;
  private int evictionRunningCount = 0;

  public AbstractEviction(final HeapCache _heapCache, final HeapCacheListener _listener, final Cache2kConfiguration cfg, final int evictionSegmentCount) {
    heapCache = _heapCache;
    listener = _listener;
    chunkSize = 4;
    if (cfg.getEntryCapacity() <= 1000) {
      maxSize = cfg.getEntryCapacity() / evictionSegmentCount;
      chunkSize = 1;
    } else {
      maxSize = (cfg.getEntryCapacity() + chunkSize) / evictionSegmentCount;
    }
    noListenerCall = _listener instanceof HeapCacheListener.NoOperation;
  }

  @Override
  public void execute(final Entry e) {
    Entry[] _evictionChunk = null;
    synchronized (lock) {
      if (e.isNotYetInsertedInReplacementList()) {
        insertIntoReplacementList(e);
        newEntryCounter++;
        _evictionChunk = fillEvictionChunk();
      } else {
        removeEventually(e);
      }
    }
    evictChunk(_evictionChunk);
  }

  Entry[] reuseChunkArray() {
    Entry[] ea = evictChunkReuse;
    if (ea != null) {
      evictChunkReuse = null;
    } else {
      return new Entry[chunkSize];
    }
    return ea;
  }

  private void removeEventually(final Entry e) {
    if (!e.isRemovedFromReplacementList()) {
      removeEntryFromReplacementList(e);
      long nrt = e.getNextRefreshTime();
      if (nrt == (Entry.GONE + Entry.EXPIRED)) {
        expiredRemovedCnt++;
      } else if (nrt == (Entry.GONE + Entry.VIRGIN)) {
        virginRemovedCnt++;
      } else {
        removedCnt++;
      }
    }
  }

  @Override
  public boolean executeWithoutEviction(final Entry e) {
    synchronized (lock) {
      if (e.isNotYetInsertedInReplacementList()) {
        insertIntoReplacementList(e);
        newEntryCounter++;
      } else {
        removeEventually(e);
      }
      return evictionNeeded();
    }
  }

  boolean evictionNeeded() {
    return getSize() > (maxSize - evictionRunningCount);
  }

  @Override
  public void evictEventually() {
    Entry[] _chunk;
    synchronized (lock) {
      _chunk = fillEvictionChunk();
    }
    evictChunk(_chunk);
  }

  @Override
  public void evictEventually(final int hc) {
    evictEventually();
  }

  private Entry[] fillEvictionChunk() {
    if (!evictionNeeded()) {
      return null;
    }
    final Entry[] _chunk = reuseChunkArray();
    evictionRunningCount += _chunk.length;
    for (int i = 0; i < _chunk.length; i++) {
      _chunk[i] = findEvictionCandidate(null);
    }
    return _chunk;
  }

  private void evictChunk(Entry[] _chunk) {
    int _evictSpins = 5;
    int _evictedCount = 0;
    int _goneCount = 0;
    int _processingCount = 0;
    int _alreadyEvicted = 0;
    for (;;) {
      if (_chunk == null) { return; }
      for (int i = 0; i < _chunk.length; i++) {
        Entry e = _chunk[i];
        if (noListenerCall) {
          synchronized (e) {
            if (e.isGone()) {
              _goneCount++;
              _chunk[i] = null;
              continue;
            }
            if ( e.isProcessing()) {
              _processingCount++;
              _chunk[i] = null;
              continue;
            }
            boolean f = heapCache.removeEntryForEviction(e);
          }
        } else {
          synchronized (e) {
            if (e.isGone() || e.isProcessing()) {
              _chunk[i] = null;
              continue;
            }
            e.startProcessing(Entry.ProcessingState.EVICT);
          }
          listener.onEvictionFromHeap(e);
          synchronized (e) {
            e.processingDone();
            boolean f = heapCache.removeEntryForEviction(e);
          }
        }
      }
      synchronized (lock) {
        for (int i = 0; i < _chunk.length; i++) {
          Entry e = _chunk[i];
          if (e != null) {
            if (!e.isRemovedFromReplacementList()) {
              evictEntry(e);
              evictedCount++;
              _evictedCount++;
            } else {
              _alreadyEvicted++;
            }
            /* we reuse the chunk array, null the array position to avoid memory leak */
            _chunk[i] = null;
          }
        }
        evictionRunningCount -= _chunk.length;
        if (evictionNeeded()) {
          if (--_evictSpins > 0) {
            _chunk = fillEvictionChunk();
          } else {
            evictChunkReuse = _chunk;
            return;
          }
        } else {
          evictChunkReuse = _chunk;
          return;
        }
      }
    }
  }

  @Override
  public long getNewEntryCount() {
    return newEntryCounter;
  }

  @Override
  public long getRemovedCount() {
    return removedCnt;
  }

  @Override
  public long getVirginRemovedCount() {
    return virginRemovedCnt;
  }

  @Override
  public long getExpiredRemovedCount() {
    return expiredRemovedCnt;
  }

  @Override
  public long getEvictedCount() {
    return evictedCount;
  }

  @Override
  public long getMaxSize() {
    return maxSize;
  }

  @Override
  public int getEvictionRunningCount() {
    return evictionRunningCount;
  }

  @Override
  public void start() { }

  @Override
  public void stop() { }

  @Override
  public boolean drain() {
    return false;
  }

  @Override
  public void close() { }

  @Override
  public <T> T runLocked(final Job<T> j) {
    synchronized (lock) {
      return j.call();
    }
  }

  protected void evictEntry(Entry e) { removeEntryFromReplacementList(e); }

  protected abstract Entry findEvictionCandidate(Entry e);
  protected abstract void removeEntryFromReplacementList(Entry e);
  protected abstract void insertIntoReplacementList(Entry e);

}
