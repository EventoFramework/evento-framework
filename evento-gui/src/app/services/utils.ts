/* eslint-disable @typescript-eslint/naming-convention,no-bitwise */
export const stringToColour = (str) => {
  let i;
  let hash = 0;
  for (i = 0; i < str.length; i++) {
    hash = str.charCodeAt(i) + ((hash << 5) - hash);
  }
  let colour = '#';
  for (i = 0; i < 3; i++) {
    const value = (hash >> (i * 8)) & 0xFF;
    colour += ('00' + value.toString(16)).substr(-2);
  }
  return colour;
};
export const getColorForBundle = (bundle: string) => {
  if (bundle === 'server') {
    return 'transparent';
  }
  if (bundle === 'event-store') {
    return 'transparent';
  }
  return stringToColour(bundle);
};


export const componentColor = {
  Aggregate: 'blue',
  Service: 'red',
  Projection: 'green',
  Projector: 'lightgreen',
  Saga: 'purple',
  Observer: 'black',
  Invoker: 'grey',
};

export const payloadColor = {
  DomainCommand: '#3399fe',
  ServiceCommand: '#ff68b9',
  DomainEvent: '#ff992a',
  ServiceEvent: '#cb3234',
  View: '#5fc08b',
  Invocation: 'grey',
  Query: 'gold',
};

export const graphCenterFit = (graph, container) => {
  const bounds = graph.getGraphBounds();
  graph.fit();
  let zoom = graph.view.scale;
  if (bounds.width > container.offsetWidth || bounds.height > container.offsetHeight) {
    zoom = Math.min(container.offsetWidth / bounds.width, container.offsetHeight / bounds.height);
  }
  zoom = zoom * 0.95;
  graph.zoomTo(zoom);
  graph.center();
  container.style.position = "absolute";
};
