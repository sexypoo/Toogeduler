export type User={id:number;email:string;name:string;avatarUrl:string};
export type Visibility="PRIVATE"|"GROUP"|"FRIENDS"|"PUBLIC";
export type CalendarEvent={id:number;title:string;description:string;location:string;startAt:string;endAt:string;allDay:boolean;visibility:Visibility;color:string;owner:User;recurrenceRule:string;publicToken:string;groupIds:number[]};
export type Group={id:number;name:string;color:string;ownerId:number;members:(User&{role:"OWNER"|"MEMBER"})[];inviteUrl:string};
export type Slot={startAt:string;endAt:string;availableCount:number;totalCount:number;availablePeople:string[];everyoneAvailable:boolean};

