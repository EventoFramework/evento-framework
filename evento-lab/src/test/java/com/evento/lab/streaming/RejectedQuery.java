package com.evento.lab.streaming;

import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.query.Single;

/** Its handler refuses it before producing any view. */
public class RejectedQuery extends Query<Single<ExportView>> {
}
