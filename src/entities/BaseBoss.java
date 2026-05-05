package entities;

import gamestates.Playing;
import main.Game;
import utilz.LoadSave;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import static utilz.Constants.EnemyConstants.*;
import static utilz.Constants.BossConstants.*;

public abstract class BaseBoss extends Enemy {

    protected int phase = 1;
    protected boolean phaseTransitioned = false;
    protected int attackCooldown = 0;
    protected Playing playing;
    protected ArrayList<Projectile> projectiles = new ArrayList<>();

    // animation
    protected BufferedImage[] moveFrames;
    protected BufferedImage[] attackFrames;
    protected BufferedImage[] hitFrames;
    protected BufferedImage[] deadFrames;
    protected int aniTick = 0, aniIndex = 0;
    protected static final int ANI_SPEED = 18;

    // boss bar
    public static final int BAR_WIDTH  = 500;
    public static final int BAR_HEIGHT = 22;

    public BaseBoss(float x, float y, int width, int height,
                    int enemyType, Playing playing) {
        super(x, y, width, height, enemyType);
        this.playing       = playing;
        this.maxHealth     = GetMaxHealthBoss(enemyType);
        this.currentHealth = maxHealth;
    }

    System.out.println("ok");

    protected abstract void loadFrames();
    protected abstract void updateAI(int[][] lvlData, Player player);
    protected abstract void doAttack(Player player);
    protected abstract int  getAttackCooldown();   // phase 1 CD in frames
    protected abstract int  getPhase2Cooldown();   // phase 2 CD in frames
    protected abstract String getBossName();

    public void update(int[][] lvlData, Player player) {
        if (currentHealth <= 0) {
            updateDeadAnimation();
            return;
        }

        checkPhaseTransition();

        if (attackCooldown > 0) attackCooldown--;
        else {
            doAttack(player);
            attackCooldown = (phase == 2) ? getPhase2Cooldown() : getAttackCooldown();
        }

        updateAI(lvlData, player);
        updateProjectiles(lvlData, player);
        updateAnimationTick();
    }

    protected void checkPhaseTransition() {
        if (!phaseTransitioned && currentHealth < maxHealth * 0.5f) {
            phase = 2;
            phaseTransitioned = true;
            onPhaseTransition(); // hook for subclasses
        }
    }

    // override in subclass if phase 2 needs special setup
    protected void onPhaseTransition() {}

    protected void updateProjectiles(int[][] lvlData, Player player) {
        projectiles.removeIf(p -> !p.isActive());
        for (Projectile p : projectiles) {
            p.update(lvlData);
            if (p.isActive() && p.getHitbox().intersects(player.getHitbox()))
                onProjectileHit(p, player);
        }
    }

    // override to apply special effects (e.g. burn)
    protected void onProjectileHit(Projectile p, Player player) {
        player.changeHealth(-GetEnemyDmgBoss(enemyType));
        p.setInactive();
    }

    protected void updateAnimationTick() {
        aniTick++;
        if (aniTick >= ANI_SPEED) {
            aniTick = 0;
            aniIndex++;
            BufferedImage[] current = getCurrentFrames();
            if (current != null && aniIndex >= current.length) {
                aniIndex = 0;
                onAnimationComplete();
            }
        }
    }

    protected void onAnimationComplete() {} // hook — boss can react when anim ends

    protected void updateDeadAnimation() {
        aniTick++;
        if (aniTick >= ANI_SPEED) {
            aniTick = 0;
            aniIndex++;
            if (deadFrames != null && aniIndex >= deadFrames.length) {
                aniIndex = deadFrames.length - 1; // freeze on last frame
                active = false;
            }
        }
    }

    protected BufferedImage[] getCurrentFrames() {
        if (currentHealth <= 0)        return deadFrames;
        if (state == BOSS_HIT)         return hitFrames;
        if (state == BOSS_ATTACK1
         || state == BOSS_ATTACK2)     return attackFrames;
        return moveFrames;
    }

    protected BufferedImage[] loadStrip(String path, int frameCount) {
        BufferedImage sheet = LoadSave.GetSpriteAtlas(path);
        if (sheet == null) return new BufferedImage[frameCount];
        int fw = sheet.getWidth() / frameCount;
        int fh = sheet.getHeight();
        BufferedImage[] frames = new BufferedImage[frameCount];
        for (int i = 0; i < frameCount; i++)
            frames[i] = sheet.getSubimage(i * fw, 0, fw, fh);
        return frames;
    }

    protected void walkToward(Player player, float speed) {
        float dir = (player.getHitbox().x > hitbox.x) ? 1 : -1;
        hitbox.x += dir * speed;
    }

    public void draw(Graphics g, int lvlOffset) {
        BufferedImage[] frames = getCurrentFrames();
        if (frames == null || aniIndex >= frames.length) return;
        g.drawImage(frames[aniIndex],
                (int)(hitbox.x) - lvlOffset,
                (int)(hitbox.y),
                width, height, null);
    }

    public void drawBossBar(Graphics g) {
        int barX = (Game.GAME_WIDTH - BAR_WIDTH) / 2;
        int barY = Game.GAME_HEIGHT - 50;

        // background
        g.setColor(new Color(30, 0, 0));
        g.fillRect(barX, barY, BAR_WIDTH, BAR_HEIGHT);

        // health fill — orange in phase 2
        float pct = (float) currentHealth / maxHealth;
        g.setColor(phase == 2 ? new Color(255, 120, 0) : new Color(200, 0, 0));
        g.fillRect(barX, barY, (int)(BAR_WIDTH * pct), BAR_HEIGHT);

        // border
        g.setColor(Color.WHITE);
        g.drawRect(barX, barY, BAR_WIDTH, BAR_HEIGHT);

        // name
        g.setFont(new Font("Arial", Font.BOLD, 14));
        g.setColor(Color.WHITE);
        g.drawString(getBossName(), barX, barY - 6);

        // phase 2 label
        if (phase == 2) {
            g.setColor(new Color(255, 120, 0));
            g.drawString("ENRAGED", barX + BAR_WIDTH - 80, barY - 6);
        }
    }

    public ArrayList<Projectile> getProjectiles() { return projectiles; }
    public boolean isActive()                      { return active; }
}