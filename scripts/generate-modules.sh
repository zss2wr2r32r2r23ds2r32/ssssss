#!/bin/bash
set -e
REF="origin/cursor/sharded-core-plugin-4bff"
WS="/workspace"
MOD="$WS/src/main/java/com/shardedcore/modules"
RES="$WS/src/main/resources/modules"

port_java() {
  local src_pkg=$1 dest_pkg=$2 dest_dir=$3 file=$4 module_id=$5
  git -C "$WS" show "$REF:src/main/java/com/sharded/core/modules/$src_pkg/$file" | \
  sed -e 's/com\.sharded\.core/com.shardedcore/g' \
      -e "s/modules\.$dest_pkg/modules.${dest_pkg//\//.}/g" \
      -e 's/protected void onEnable/public void enable/g' \
      -e 's/protected void onDisable/public void disable/g' \
      -e 's/moduleFolder()/moduleFolder/g' \
      -e 's/Text\.c(/ColorUtil.parse(/g' \
      -e 's/PlaceholderUtil\.apply(/Text.applyPlaceholders(/g' \
      -e 's/PlaceholderUtil\.applyList(/Text.applyPlaceholderList(/g' \
      -e 's/plugin\.luckPerms()/LuckPermsHelper/g' \
      -e 's/sharded\.teams\.use/shardedcore.command.team/g' \
      -e 's/sharded\.killrewards\.use/shardedcore.command.killrewards/g' \
      -e 's/sharded\.playtimerewards\.use/shardedcore.command.playtimerewards/g' \
      -e 's/sharded\.joincounter\.admin/shardedcore.joincounter.admin/g' \
      -e 's/sharded\.settings\.joinmessages/shardedcore.command.jointoggle/g' \
      -e 's/sharded\.media\.use/shardedcore.command.media/g' \
      -e 's/sharded\.crates\.use/shardedcore.command.crate/g' \
      -e "s/super(plugin, \"$src_pkg\")/super(plugin, \"$module_id\")/g" \
      -e 's/GuiFooters\.view()/"\&7Click to view"/g' \
      -e 's/GuiFooters\.confirm()/"\&aClick to confirm"/g' \
      -e 's/GuiFooters\.cancel()/"\&cClick to cancel"/g' \
      -e 's/GuiFooters\.create()/"\&aClick to create"/g' \
  > "$dest_dir/$file"
}

copy_config() {
  local src=$1 dst=$2
  mkdir -p "$RES/$dst"
  for f in config.yml messages.yml gui.yml; do
    git -C "$WS" show "$REF:src/main/resources/modules/$src/$f" > "$RES/$dst/$f" 2>/dev/null || rm -f "$RES/$dst/$f"
  done
}

mkdir -p "$MOD"/{dropfix,deathmessages,nametags,staff,joincounter,media,killrewards,playtimerewards,team,rtp,crates}

copy_config dropfix dropfix
copy_config deathmessages death-messages
copy_config nametags nametags
copy_config joincounter join-counter
copy_config media media
copy_config killrewards kill-rewards
copy_config playtimerewards playtime-rewards
copy_config teams team
copy_config crates crates

# Fix prefixes in configs
sed -i "s/&#FCFF00&lTEAM/&#FF00F8&lTEAM/g" "$RES/team/config.yml" 2>/dev/null || true
sed -i "1,20s/prefix:.*/prefix: '&#FF00F8&lTEAM &8▷ &r'/" "$RES/team/config.yml" 2>/dev/null || true

echo "Configs copied"
