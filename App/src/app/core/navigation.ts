/** Who is looking at the menu: the kinds of people that chapter 3.1 and 11.2 tell apart. */
export interface Viewer {
  signedIn: boolean;
  /** Runs Minecraft servers (can create games on them). */
  serverAdmin: boolean;
  siteAdmin: boolean;
}

export interface NavItem {
  id: string;
  route: string;
  /** The words on the link, in the language of the build. */
  label: () => string;
}

interface NavEntry extends NavItem {
  /** Who sees it. */
  visibleTo: (viewer: Viewer) => boolean;
}

const signedIn = (v: Viewer) => v.signedIn;
const canCreateGames = (v: Viewer) => v.signedIn && (v.serverAdmin || v.siteAdmin);

/**
 * Every entry of the main menu, in the order shown. Only screens that exist are listed: the admin panel (11.4) is reached from inside
 * a game, and the site administration screens (11.2) will add their entries here when they are built.
 */
const ENTRIES: readonly NavEntry[] = [
  { id: 'games', route: '/games', label: () => $localize`:@@navGames:Games`, visibleTo: signedIn },
  { id: 'new-game', route: '/games/new', label: () => $localize`:@@navNewGame:New game`, visibleTo: canCreateGames },
  { id: 'cards', route: '/cards', label: () => $localize`:@@navCards:Cards`, visibleTo: signedIn },
  { id: 'export', route: '/cards/export', label: () => $localize`:@@navExport:Export`, visibleTo: signedIn },
  { id: 'account', route: '/account', label: () => $localize`:@@navAccount:Account`, visibleTo: signedIn },
];

/** The menu for this person: nothing before sign-in, the player's menu after, and "New game" for people who run servers. */
export function navigationFor(viewer: Viewer): NavItem[] {
  return ENTRIES.filter((e) => e.visibleTo(viewer)).map(({ id, route, label }) => ({ id, route, label }));
}

/**
 * The entry to highlight for this address: the one with the longest route that the address is (or is inside of), so that `/games/new`
 * lights "New game" and not "Games", and `/games/5/lobby` lights "Games".
 */
export function activeNavId(url: string, items: readonly NavItem[]): string | null {
  const path = url.split(/[?#]/)[0];
  let best: NavItem | null = null;
  for (const item of items) {
    if ((path === item.route || path.startsWith(item.route + '/')) && (!best || item.route.length > best.route.length)) {
      best = item;
    }
  }
  return best?.id ?? null;
}
