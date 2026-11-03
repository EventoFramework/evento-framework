export const stringToColour = (str) => {
  let i;
  let hash = 0;
  for (i = 0; i < str.length; i++) {
    hash = str.charCodeAt(i) + ((hash << 5) - hash);
  }
  // Muted, so a name-derived color never competes with the color code of the console.
  return `hsl(${Math.abs(hash) % 360}, 30%, 48%)`;
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


/**
 * The color code of the console: the single source for chips, dots, cards and graphs. Each color
 * has a value and a readable text color per theme. Payload hues follow the three kinds of message:
 * blue and pink for Commands, orange and red for Events, gold for Queries.
 */
interface ColorCode {
  light: string;
  dark: string;
  onLight: string;
  onDark: string;
}

const INK = '#001a1a';
const WHITE = '#ffffff';
const code = (light: string, dark: string, onLight: string, onDark = INK): ColorCode => ({light, dark, onLight, onDark});
const same = (color: string, on: string): ColorCode => ({light: color, dark: color, onLight: on, onDark: on});

const COMPONENT_CODE: Record<string, ColorCode> = {
  Invoker: code('#66706e', '#a9b4b2', WHITE),
  Aggregate: code('#0b3bff', '#5b9bff', WHITE),
  Service: code('#d40022', '#ff4d6a', WHITE),
  Projector: code('#0b6b2e', '#27c46b', WHITE),
  Projection: code('#2f9e44', '#b4f5a8', INK),
  Saga: code('#7a1fb8', '#c08cff', WHITE),
  Observer: code('#001a1a', '#fff4ec', WHITE),
};

const PAYLOAD_CODE: Record<string, ColorCode> = {
  DomainCommand: same('#3399fe', INK),
  ServiceCommand: same('#ff68b9', INK),
  DomainEvent: same('#ff992a', INK),
  ServiceEvent: code('#cb3234', '#ff6b6b', WHITE),
  Query: same('#ffd700', INK),
  View: same('#00cc00', INK),
  Invocation: same('#808080', INK),
};

/** The seven shapes of the brand, one per component, on a 57x40 box. */
const SHAPE_BOX_WIDTH = 57;
const COMPONENT_SHAPE: Record<string, (fill: string) => string> = {
  Invoker: (fill) => `<polygon fill="${fill}" points="8.5,0 48.5,0 48.5,40 8.5,40"/>`,
  Aggregate: (fill) => `<polygon fill="${fill}" points="28.5,0 51.5,40 5.5,40"/>`,
  Service: (fill) => `<polygon fill="${fill}" points="17,0 40,0 51.5,20 40,40 17,40 5.5,20"/>`,
  Projector: (fill) => `<polygon fill="${fill}" points="28.5,0 48.5,20 28.5,40 8.5,20"/>`,
  Projection: (fill) => `<circle fill="${fill}" cx="28.5" cy="20" r="20"/>`,
  Saga: (fill) => `<polygon fill="${fill}" points="2,0 40.2,0 55,20 40.2,40 2,40 13.7,20"/>`,
  Observer: (fill) => `<polygon fill="${fill}" points="13.7,0 57,0 43.3,40 0,40"/>`,
};

/** A component's shape as an SVG data URI, or null for a type without one. */
export const componentShape = (type: string, fill = '#000'): string | null => {
  const shape = COMPONENT_SHAPE[type];
  if (!shape) {
    return null;
  }
  // Explicit size: the canvas rasterizes at it, and a small one blurs when zoomed in.
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${SHAPE_BOX_WIDTH} 40" width="228" height="160">${shape(fill)}</svg>`;
  return 'data:image/svg+xml;utf8,' + encodeURIComponent(svg);
};

const isDark = () => document.documentElement.classList.contains('ion-palette-dark');

/** Calls back when light and dark swap. Returns the function that stops watching. */
export const onThemeChange = (callback: () => void): (() => void) => {
  let dark = isDark();
  const observer = new MutationObserver(() => {
    if (dark !== isDark()) {
      dark = isDark();
      callback();
    }
  });
  observer.observe(document.documentElement, {attributes: true, attributeFilter: ['class']});
  return () => observer.disconnect();
};

// Validated with the dataviz palette checks against foam and ink.
const CHART_SERIES = {
  light: {count: '#0a8aad', lock: '#c2410c', retrieve: '#1d5fd1', service: '#0f8a5f', store: '#8a3ffc'},
  dark: {count: '#2c9fbd', lock: '#ee6030', retrieve: '#4a86ea', service: '#17a06c', store: '#a673f0'},
};

/** Series colors and chrome for the telemetry charts, in the theme now active. */
export const chartTheme = () => {
  const token = (name: string) => getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return {
    series: isDark() ? CHART_SERIES.dark : CHART_SERIES.light,
    tooltip: isDark() ? 'dark' : 'light',
    text: token('--evento-text-muted'),
    grid: token('--evento-border'),
    font: token('--evento-font-ui'),
  };
};

const inCurrentTheme = (table: Record<string, ColorCode>) =>
  new Proxy({} as Record<string, string>, {
    get: (_, key) => {
      const entry = table[key as string];
      return entry && (isDark() ? entry.dark : entry.light);
    },
  });

export const componentColor = inCurrentTheme(COMPONENT_CODE);
export const payloadColor = inCurrentTheme(PAYLOAD_CODE);

/** The same code color in the theme now active, for graphs recoloring on a theme flip. */
export const colorForCurrentTheme = (color: string): string => {
  const entry = [...Object.values(COMPONENT_CODE), ...Object.values(PAYLOAD_CODE)]
    .find((c) => c.light === color || c.dark === color);
  return entry ? (isDark() ? entry.dark : entry.light) : color;
};

/** Generates the chip, card and dot rules from the tables above. Call once, at startup. */
export const installColorCode = (): void => {
  const rules = (prefix: string, table: Record<string, ColorCode>) =>
    Object.entries(table).map(([name, c]) => `
      .${prefix}-${name} { --background: ${c.light}; --color: ${c.onLight}; }
      .ion-palette-dark .${prefix}-${name} { --background: ${c.dark}; --color: ${c.onDark}; }
      .dot.${prefix}-${name}, .shape.${prefix}-${name} { color: ${c.light}; }
      .ion-palette-dark .dot.${prefix}-${name}, .ion-palette-dark .shape.${prefix}-${name} { color: ${c.dark}; }`).join('');
  const shapes = Object.keys(COMPONENT_SHAPE).map((name) =>
    `.component-type-${name} { --shape: url("${componentShape(name)}"); }`).join('');
  const style = document.createElement('style');
  style.textContent = rules('component-type', COMPONENT_CODE) + rules('payload-type', PAYLOAD_CODE) + shapes;
  document.head.appendChild(style);
};

/**
 * What a handler outputs, as the catalog draws it: for a command handler every event it may
 * produce (3.0.1: a DomainEvents handler declares several), otherwise its return type (a query
 * handler's view). A server before 3.0.1 sends no producedEvents: the return type then.
 */
export function handlerOutputs(h: any): Array<{ name: string; type: string }> {
  if (h?.producedEvents?.length) {
    return h.producedEvents;
  }
  return h?.returnType ? [h.returnType] : [];
}
