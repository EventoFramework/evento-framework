import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  standalone: true,
  name: 'repositoryLogo'
})
export class RepositoryLogoPipe implements PipeTransform {

  transform(value: string): string {
    if(value.includes('github')){
      return 'logo-github'
    }
    if(value.includes('gitlab')){
      return 'logo-gitlab'
    }
    if(value.includes('bitbucket')){
      return 'logo-bitbucket'
    }
    return 'open-outline';
  }

}
