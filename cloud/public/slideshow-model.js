'use strict';
// Pure playlist rules, shared by the browser and executable Node regression tests.
(function(root){
  class Playlist {
    constructor(random=Math.random){this.photos=new Map();this.queue=[];this.order='newest';this.random=random;}
    ordered(ids){const out=[...ids];out.sort((a,b)=>(this.photos.get(b)?.seq||0)-(this.photos.get(a)?.seq||0));if(this.order==='oldest')out.reverse();if(this.order==='shuffle')for(let i=out.length-1;i>0;i--){const j=Math.floor(this.random()*(i+1));[out[i],out[j]]=[out[j],out[i]];}return out;}
    update(photos,order){const old=this.photos;this.photos=new Map(photos.map(p=>[p.id,p]));const changed=this.order!==order;this.order=order;
      if(!old.size||changed){this.queue=this.ordered(this.photos.keys());return;}
      const newIds=photos.filter(p=>!old.has(p.id)).map(p=>p.id);
      this.queue=[...newIds,...this.queue.filter(id=>this.photos.has(id)&&!newIds.includes(id))];
    }
    next(current){if(!this.photos.size)return null;if(!this.queue.length)this.queue=this.ordered(this.photos.keys());
      // Avoid an immediate repeat across cycle boundaries when another photo exists.
      if(this.queue[0]===current&&this.photos.size>1){this.queue.shift();if(!this.queue.length)this.queue=this.ordered([...this.photos.keys()].filter(id=>id!==current));}
      return this.photos.get(this.queue.shift())||null;
    }
    clear(){this.photos.clear();this.queue=[];}
  }
  if(typeof module!=='undefined'&&module.exports)module.exports={Playlist};
  else root.LensDropPlaylist=Playlist;
})(typeof window!=='undefined'?window:globalThis);
