# EasyTrade

A Hytale server plugin for safe player-to-player item trading with an interactive UI and atomic transactions.

## Features

- **Interactive Trading UI** - Select items for trading with quantity controls
- **Safe Atomic Transactions** - Items are exchanged simultaneously or not at all
- **Smart Inventory Validation** - Intelligently checks if items can stack with existing inventory
- **Countdown System** - 3-second countdown before trade execution to prevent accidents
- **Customizable Messages** - Fully translatable message system with color code support
- **Test Mode** - Solo testing for development and debugging
- **Update Checker** - Automatically checks GitHub and CurseForge for new versions
- **Debug Mode** - Detailed logging for troubleshooting

## How It Works

1. Player A sends a trade request to Player B: `/trade request PlayerB`
2. Player B accepts the request: `/trade accept`
3. Both players open the trading UI and add items to their offers
4. Both players click **ACCEPT** when ready
5. 3-second countdown begins - both players can still cancel
6. After countdown, both players click **CONFIRM** to complete the trade
7. Items are exchanged atomically (all or nothing)

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/trade request <player>` | Send a trade request to another player | `easytrade.trade.request` |
| `/trade accept` | Accept a pending trade request | `easytrade.trade.accept` |
| `/trade decline` | Decline a pending trade request | `easytrade.trade.decline` |
| `/trade cancel` | Cancel current trade | `easytrade.trade.cancel` |
| `/trade confirm` | Confirm trade after countdown | `easytrade.trade.confirm` |
| `/trade open` | Open trading UI | `easytrade.trade.open` |
| `/trade reload` | Reload config and messages | `easytrade.admin.reload` |
| `/trade test` | Start solo test trade (debug mode only) | `easytrade.admin.test` |
| `/trade help` | Show help message | `easytrade.trade.help` |

**Note:** All permissions default to true for all players except admin commands.

## Configuration

Configuration is automatically saved to `mods/Toskan4134_EasyTrade/EasyTrade.json`

| Option | Default | Description                                           |
|--------|---------|-------------------------------------------------------|
| `Debug` | `false` | Enable detailed debug logging                         |
| `RequestTimeoutSeconds` | `30000` | Miliseconds before trade request expires              |
| `CountdownDurationSeconds` | `3000`  | Miliseconds countdown duration before trade execution |
| `CheckForUpdates` | `true`  | Check for plugin updates on startup                   |

### Example Configuration

```json
{
  "CountdownDuration": 3000,
  "RequestTimeout": 30000,
  "CheckForUpdates": true,
  "Debug": true
}
```

## Messages

Messages are stored in `plugins/EasyTrade/messages.json` and support color codes and placeholders.

### Color Codes

- `&0-9, &a-f` - Standard Minecraft colors
- `&#RRGGBB` - Hex colors (e.g., `&#FF0000` for red)
- `&l` - Bold text
- `&r` - Reset formatting

### Placeholders

- `{player}` - Player name
- `{target}` - Target player name
- `{seconds}` - Countdown/timeout seconds
- `{version}` - Plugin version

### Example Messages

```properties
trade.request.sent=&aTrade request sent to &f{target}
trade.request.received=&eTrade request received from &f{initiator}&e. Use &6/trade accept &7to accept.
trade.status.countdown=&aBoth accepted! Completing in &f{seconds}s&a...
ui.status.notEnoughSpace=You don't have enough inventory space
```
> You can find some messages.json examples [HERE](https://github.com/Toskan4134/EasyTrade/tree/main/examples/messages)

## Trading UI

The trading UI is divided into sections:

- **Your Inventory** - Items available to offer. It searches on inventory, hotbar and backpack (bottom)
- **Your Offer** - Items you're offering (left)
- **Partner Offer** - Items your partner is offering (right)
- **Status Message** - Shows trade state and instructions
- **Action Buttons** - ACCEPT, CONFIRM, CANCEL

### Adding Items to Offer

- **Click item** - Add 1 full stack
- **Click +1 button** - Add 1 item to existing offer
- **Click +10 button** - Add 10 items to existing offer

### Removing Items from Offer

- **Click offered item** - Remove 1 full stack
- **Click -1 button** - Remove 1 item from offer
- **Click -10 button** - Remove 10 items from offer

### Trade States (Debugging)

1. **NEGOTIATING** - Both players can modify offers
2. **ONE_ACCEPTED** - One player has accepted, waiting for other
3. **BOTH_ACCEPTED_COUNTDOWN** - Countdown in progress (3 seconds)
4. **EXECUTING** - Trade is being executed (atomic transaction)
5. **COMPLETED** - Trade successful
6. **CANCELLED** - Trade was cancelled
7. **FAILED** - Trade failed (validation error, inventory issues)

### Safety Features

- **Auto-unaccept** - If inventory changes, acceptance is automatically revoked
- **Inventory validation** - Checks if items are still available before executing
- **Smart space validation** - Intelligently checks if items can stack with existing inventory
- **Atomic transactions** - Either all items are exchanged or none (rollback on failure)
- **Partner disconnect handling** - Trade is cancelled and UI closes if partner disconnects

## Permissions

All permissions follow the pattern `easytrade.<category>.<command>`.
You can modify permissions using `/perm group/user list/add/remove <group/user>`

### Default Permissions (All Players)

These permissions should be granted to everyone:

```
easytrade.trade.request   - Send trade requests
easytrade.trade.accept    - Accept trade requests
easytrade.trade.decline   - Decline trade requests
easytrade.trade.cancel    - Cancel active trades
easytrade.trade.confirm   - Confirm trades after countdown
easytrade.trade.open      - Open trading UI
easytrade.trade.help      - View help message
```

### Admin Permissions

These permissions should be restricted to administrators/operators:

```
easytrade.admin.reload    - Reload configuration and messages
easytrade.admin.test      - Start solo test trades (requires debug mode)
```

### Default Behavior

**By default, NO permissions are required:**
- ✅ All players can use all trading commands (no permissions needed)
- ❌ Only `/trade reload` requires permissions (`easytrade.admin.reload`)
- 🐞 Command `/trade test` requires `debug=true` on config and permissions (`easytrade.admin.test`)

**You only need to configure permissions if you want to RESTRICT access to trading commands.**

## Installation

1. Build the plugin JAR file or download from releases
2. Place the JAR in your server's `mods` folder
3. Start/restart the server
4. Configuration and messages files will be auto-generated
5. Customize messages in `plugins/EasyTrade/messages.json` and configuration in `mods/Toskan4134_EasyTrade/EasyTrade.json` if desired

## Building

```bash
./gradlew build
```

The compiled JAR will be located in `build/libs/`

### Development Setup

1. Clone the repository
2. Import into IntelliJ IDEA as a Gradle project
3. Configure run configuration to point to Hytale server
4. Enable debug mode in config for detailed logging

## Update Checker

The plugin automatically checks for updates from GitHub and CurseForge:
- Checks on server startup
- Checks every 12 hours while the server is running
- Logs to console when a new version is available
- Notifies operators (players with `*` permission) when they join

Set `CheckForUpdates` to `false` in config to disable.

## License

MIT License

## Author

Toskan4134

## Links

- **GitHub**: https://github.com/Toskan4134/EasyTrade
- **CurseForge**: https://www.curseforge.com/hytale/mods/easytrade
