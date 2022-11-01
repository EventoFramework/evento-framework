export class ApplicationMap{
  bundles: Bundle[]
  edges: {}
}

export class Bundle{
  id: string;
  name: string;
  components: Component[]
}

export class Component{
  id: string;
  name: string;
  handledMessages: Message[]
}

export class Message{
  id: string;
  name: string;
}
