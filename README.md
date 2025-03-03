<div align="center">

# Smart Spawner

### Ultimate GUI Spawner Plugin for Minecraft Servers

[![Version](https://img.shields.io/github/v/release/ptthanh02/Smart-Spawner-Plugin?color=4B32C3&logo=github&style=for-the-badge)](https://github.com/ptthanh02/Smart-Spawner-Plugin/releases/latest)
[![Downloads](https://img.shields.io/modrinth/dt/smart-spawner-plugin?style=for-the-badge&logo=modrinth&logoColor=white&label=Downloads&color=00AF5C)](https://modrinth.com/plugin/smart-spawner-plugin)
[![Rating](https://img.shields.io/spiget/rating/120743?style=for-the-badge&logo=spigotmc&logoColor=white&label=Spigot&color=FF8800)](https://www.spigotmc.org/resources/120743/)
[![License](https://img.shields.io/badge/License-CC%20BY--NC--SA%204.0-7289DA?style=for-the-badge&logo=creative-commons&logoColor=white)](LICENSE)

Enhances spawner usage with an intuitive GUI, enabling automated mob drops and experience generation without spawning entities.

[<img src="https://raw.githubusercontent.com/intergrav/devins-badges/v3/assets/cozy/available/modrinth_vector.svg" height="50">](https://modrinth.com/plugin/smart-spawner-plugin)
[<img src="https://raw.githubusercontent.com/intergrav/devins-badges/v3/assets/cozy/supported/spigot_vector.svg" height="50">](https://www.spigotmc.org/resources/120743/)

</div>

## 🚀 Getting Started

### System Requirements

- **Server**: Bukkit, Paper, or compatible forks
- **Minecraft Version**: 1.20 - 1.21.4
- **Java**: Java 21 or newer

### 📥 Installation

1. Download the latest version from [Modrinth](https://modrinth.com/plugin/smart-spawner-plugin) or [Spigot](https://www.spigotmc.org/resources/120743/)
2. Place the `.jar` file in your server's `plugins` folder
3. Restart your server
4. Configure settings in `plugins/SmartSpawner/config.yml`


## 🎮 Commands & Permissions

### Core Commands
> **Base Command:** `/smartspawner` (Aliases: `/ss`, `/spawner`)

| Command | Description                       | Permission | Default |
|---------|-----------------------------------|------------|---------|
| `/ss reload` | Reload plugin configuration       | `smartspawner.reload` | OP |
| `/ss give <player> <type> <amount>` | Give spawners to players          | `smartspawner.give` | OP |
| `/ss list` | Open admin spawner management GUI | `smartspawner.list` | OP |
| `/ss hologram` | Toggle hologram visibility        | `smartspawner.hologram` | OP |

### Player Permissions

| Permission | Description                            | Default |
|------------|----------------------------------------|---------|
| `smartspawner.stack` | Allow spawner stacking                 | true |
| `smartspawner.break` | Allow spawner breaking                 | true |
| `smartspawner.sellall` | Allow selling storage items directly   | true |
| `smartspawner.changetype` | Allow changing spawner types with eggs | OP |

## 🌍 Translations

| Language | Locale Code | Contributor                                     | Version  |
|----------|-------------|-------------------------------------------------|----------|
| [English](https://github.com/ptthanh02/Smart-Spawner/blob/main/src/main/resources/messages/en_US.yml) | `en_US` | Core language                                   | `Latest` |
| [Vietnamese](https://github.com/ptthanh02/Smart-Spawner/blob/main/src/main/resources/messages/vi_VN.yml) | `vi_VN` | Ptthanh02                                       | `Latest` |
| [Chinese Simplified](https://github.com/ptthanh02/Smart-Spawner/blob/main/src/main/resources/messages/zh_CN.yml) | `zh_CN` | [SnowCutieOwO](https://github.com/SnowCutieOwO) | `v1.2.3` |

> 🔍 **Want to help translate?** Check our [Translation Guide](https://github.com/ptthanh02/Smart-Spawner-Plugin/wiki/Translation-Guide)

## 📊 Usage Statistics

[![bStats](https://bstats.org/signatures/bukkit/SmartSpawner.svg)](https://bstats.org/plugin/bukkit/SmartSpawner)


## 💻 Developer API
<details>
<summary>Smart Spawner API</summary>

### Installation & Documentation

For API installation instructions, usage examples, and complete documentation, please visit:
[Smart Spawner API Package](https://github.com/ptthanh02/Smart-Spawner/packages/2421916)

</details>

## 🤝 Contributing

We welcome contributions! Here's how you can help:

1. 🍴 Fork the repository
2. 🌿 Create your feature branch (`git checkout -b feature/amazing-feature`)
3. 💾 Commit your changes (`git commit -m 'Add amazing feature'`)
4. 📤 Push to the branch (`git push origin feature/amazing-feature`)
5. 🔄 Create a Pull Request

## 💬 Community & Support

[![Discord Banner](https://img.shields.io/discord/1299353023532896296?style=for-the-badge&logo=discord&logoColor=white&label=Join%20our%20Discord&color=5865F2)](https://discord.gg/zrnyG4CuuT)

- 🎮 [Discord Community](https://discord.gg/zrnyG4CuuT)
- 🐛 [Issue Tracker](https://github.com/ptthanh02/Smart-Spawner-Plugin/issues)
- 📚 [Wiki Documentation](https://github.com/ptthanh02/Smart-Spawner-Plugin/wiki)

## 📜 License

This project is licensed under the **CC BY-NC-SA 4.0 License** - see the [LICENSE](LICENSE) file for details.

---

<div align="center">

**Created with ❤️ by Ptthanh02 & Contributors**

[Website](https://github.com/ptthanh02/Smart-Spawner-Plugin) • [Documentation](https://github.com/ptthanh02/Smart-Spawner-Plugin/wiki) • [Support](https://discord.gg/zrnyG4CuuT)

</div>