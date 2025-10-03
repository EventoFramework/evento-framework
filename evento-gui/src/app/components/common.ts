
declare const mxUtils: any;
export function setZoom(container, graph){
  container.addEventListener('wheel', (e: WheelEvent) => {
    e.preventDefault();
    e.stopPropagation();

    const p = mxUtils.convertPoint(container, e.clientX, e.clientY);
    const scale = graph.view.scale;
    const newScale = e.deltaY < 0 ? scale * 1.2 : scale / 1.2; // zoom in/out

    // Zoom relative to cursor
    const dx = p.x * (newScale / scale - 1);
    const dy = p.y * (newScale / scale - 1);

    graph.view.scaleAndTranslate(
      newScale,
      graph.view.translate.x - dx / newScale,
      graph.view.translate.y - dy / newScale
    );
  });
}
