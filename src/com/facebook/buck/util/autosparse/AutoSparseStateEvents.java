/*
 * Copyright 2014-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.util.autosparse;

import com.facebook.buck.event.AbstractBuckEvent;
import com.facebook.buck.event.EventKey;
import com.facebook.buck.event.LeafEvent;
import com.facebook.buck.event.WorkAdvanceEvent;

/**
 * Events posted to mark AutoSparse progress.
 */
public abstract class AutoSparseStateEvents
    extends AbstractBuckEvent
    implements LeafEvent, WorkAdvanceEvent {
  // This class does nothing; it exists only to group AbstractBuckEvents.
  private AutoSparseStateEvents(EventKey eventKey) {
    super(eventKey);
  }

  /**
   * Event posted immediately before refreshing the sparse profile
   */
  public static class SparseRefreshStarted extends AutoSparseStateEvents {
    public SparseRefreshStarted() {
      super(EventKey.unique());
    }

    @Override
    public String getEventName() {
      return "AutoSparseSparseRefreshStarted";
    }

    @Override
    public String getCategory() {
      return "autosparse";
    }

    @Override
    public String getValueString() {
      return "";
    }
  }

  /**
   * Event posted immediately after refreshing the sparse profile
   */
  public static class SparseRefreshFinished extends AutoSparseStateEvents {
    public SparseRefreshFinished(AutoSparseStateEvents.SparseRefreshStarted started) {
      super(started.getEventKey());
    }

    @Override
    public String getEventName() {
      return "AutoSparseSparseRefreshFinished";
    }

    @Override
    public String getCategory() {
      return "autosparse";
    }

    @Override
    public String getValueString() {
      return "";
    }
  }
}
