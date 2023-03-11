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
}

export const componentColor = {
  'Aggregate': 'blue',
  'Service': 'red',
  'Projection': 'green',
  'Projector': 'lightgreen',
  'Saga': 'purple',
  'Observer': 'black',
  'Invoker': 'grey',
}
export const payloadColor = {
  'DomainCommand': '#3399fe',
  'ServiceCommand': '#ff68b9',
  'DomainEvent': '#ff992a',
  'ServiceEvent': '#cb3234',
  'View': '#5fc08b',
  'Invocation': 'grey',
  'Query': 'gold',
}
