"""Official Bee Swarm Simulator launch URLs (no injection — protocol/web only)."""

BSS_PLACE_ID = 1537690962
BSS_UNIVERSE_ID = 601130232
BSS_SLUG = "Bee-Swarm-Simulator"

BSS_DEEPLINK = f"roblox://experiences/start?placeId={BSS_PLACE_ID}"
BSS_WEB_START = f"https://www.roblox.com/games/start?placeId={BSS_PLACE_ID}"
BSS_WEB_PAGE = f"https://www.roblox.com/games/{BSS_PLACE_ID}/{BSS_SLUG}"

LAUNCH_URLS = (BSS_DEEPLINK, BSS_WEB_START, BSS_WEB_PAGE)


def launch_urls() -> tuple[str, ...]:
    """URLs tried in order to open Bee Swarm Simulator."""
    return LAUNCH_URLS
