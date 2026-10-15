package com.evento.lab.versioning;

import com.evento.common.modeling.state.AggregateState;
import com.evento.common.versioning.Revision;

/** Revision 0 called the title "name". */
@Revision(1)
public class ItemState extends AggregateState {
    private String title;

    public ItemState() {}

    public ItemState(String title) { this.title = title; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}
