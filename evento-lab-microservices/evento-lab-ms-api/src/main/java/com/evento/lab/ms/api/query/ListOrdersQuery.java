package com.evento.lab.ms.api.query;

import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.query.Multiple;
import com.evento.lab.ms.api.view.OrderView;

public class ListOrdersQuery extends Query<Multiple<OrderView>> {
}
