"use client";

import { useState } from "react";
import { Info, LogOut, Shield, Trash2 } from "lucide-react";
import { ScreenHeader } from "./ScreenHeader";
import { AboutSheet } from "./AboutSheet";
import { ConfirmDialog } from "./ConfirmDialog";
import { Segmented } from "./controls";
import {
  SettingsDivider,
  SettingsLinkRow,
  SettingsRow,
  SettingsSection,
  SettingsToggle,
} from "./settings";
import { useTheme } from "./ThemeProvider";
import { prefs, type Appearance } from "@/lib/prefs";
import { analytics } from "@/lib/analytics";
import { APP_NAME, APP_TAGLINE, PRIVACY_URL } from "@/lib/constants";

export function SettingsView({
  onBack,
  email,
  onSignOut,
  onDeleteAccount,
}: {
  onBack: () => void;
  email: string | null;
  onSignOut: () => Promise<void>;
  onDeleteAccount: () => Promise<void>;
}) {
  const { appearance, setAppearance, useRoundedFont, setUseRoundedFont } =
    useTheme();
  const [haptics, setHapticsState] = useState(prefs.getHaptics());
  const [aboutOpen, setAboutOpen] = useState(false);
  const [deleteAccountOpen, setDeleteAccountOpen] = useState(false);
  const [busy, setBusy] = useState(false);

  return (
    <div className="min-h-dvh">
      <ScreenHeader title="Settings" onBack={onBack} />
      <div className="px-4 pb-12">
        <SettingsSection title="Appearance">
          <SettingsRow label="Theme">
            <Segmented<Appearance>
              className="w-56"
              options={[
                { value: "system", label: "Auto" },
                { value: "light", label: "Light" },
                { value: "dark", label: "Dark" },
              ]}
              selected={appearance}
              onSelect={(a) => {
                setAppearance(a);
                analytics.settingThemeChange(a);
              }}
            />
          </SettingsRow>
          <SettingsDivider />
          <SettingsToggle
            label="Rounded font"
            sub="Use the rounded brand typeface"
            checked={useRoundedFont}
            onChange={(v) => {
              setUseRoundedFont(v);
              analytics.settingFontChange(v ? "rounded" : "system");
            }}
          />
          <SettingsDivider />
          <SettingsToggle
            label="Haptic feedback"
            sub="Vibrate on key actions (supported devices)"
            checked={haptics}
            onChange={(v) => {
              prefs.setHaptics(v);
              setHapticsState(v);
              analytics.settingHapticsToggle(v);
            }}
          />
        </SettingsSection>

        <SettingsSection title="About">
          <SettingsLinkRow
            icon={<Info size={18} className="text-on-surface-variant" />}
            label={`About ${APP_NAME}`}
            onClick={() => {
              setAboutOpen(true);
              analytics.screenAboutOpen();
            }}
          />
          <SettingsDivider />
          <SettingsLinkRow
            icon={<Shield size={18} className="text-on-surface-variant" />}
            label="Privacy policy"
            href={PRIVACY_URL}
          />
        </SettingsSection>

        <SettingsSection title="Account">
          {email && (
            <p className="pb-2 text-sm text-on-surface-variant">
              Signed in as {email}
            </p>
          )}
          <SettingsLinkRow
            icon={<LogOut size={18} className="text-on-surface-variant" />}
            label="Sign out"
            onClick={onSignOut}
          />
          <SettingsDivider />
          <SettingsLinkRow
            icon={<Trash2 size={18} className="text-error" />}
            label="Delete account"
            onClick={() => setDeleteAccountOpen(true)}
          />
        </SettingsSection>
      </div>

      <AboutSheet
        open={aboutOpen}
        onClose={() => setAboutOpen(false)}
        appName={APP_NAME}
        description={APP_TAGLINE}
        iconSrc="/icon-512.png"
        links={[{ label: "Privacy policy", href: PRIVACY_URL }]}
      />

      <ConfirmDialog
        open={deleteAccountOpen}
        title="Delete account?"
        message="This permanently deletes your account and every institution and entry. This cannot be undone."
        confirmLabel="Delete everything"
        danger
        busy={busy}
        onClose={() => setDeleteAccountOpen(false)}
        onConfirm={async () => {
          setBusy(true);
          try {
            await onDeleteAccount();
            setDeleteAccountOpen(false);
          } finally {
            setBusy(false);
          }
        }}
      />
    </div>
  );
}
