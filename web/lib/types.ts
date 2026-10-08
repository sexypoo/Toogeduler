export type User={id:number;email:string;name:string;avatarUrl:string;friendCode:string;provider?:string;createdAt?:string};
export type UserStats={eventCount:number;groupCount:number;friendCount:number};
export type Visibility="PRIVATE"|"GROUP"|"FRIENDS"|"PUBLIC";
export type CalendarEvent={id:number;title:string;description:string;location:string;startAt:string;endAt:string;allDay:boolean;visibility:Visibility;visibilities:Visibility[];color:string;owner:User;recurrenceRule:string;reminderMinutes:number;publicToken:string;groupIds:number[];announce?:boolean;instanceStart?:string;seriesStartAt?:string;seriesEndAt?:string};
export type Group={id:number;name:string;color:string;ownerId:number;members:(User&{role:"OWNER"|"MEMBER"})[];inviteUrl:string};
export type Slot={startAt:string;endAt:string;availableCount:number;totalCount:number;availablePeople:string[];everyoneAvailable:boolean};
export type AppNotification={id:number;type:"FRIEND_REQUEST"|"FRIEND_ACCEPTED"|"GROUP_JOINED"|"EVENT_REMINDER"|"GROUP_EVENT";title:string;message:string;link:string;read:boolean;createdAt:string};
