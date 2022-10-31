export class ApplicationMap{
  nodes: Node[]
  edges: []
}

export class Node{
  id: string;
  name: string;
  children: Node[]
}
