package com.evento.lab.query;

import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.query.Multiple;
import com.evento.lab.view.OrderView;

public class ListOrdersQuery extends Query<Multiple<OrderView>> {
}
