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
import type { Institution } from "@/lib/types";

export function SettingsView({
  onBack,
  email,
  institutions,
  onDeleteInstitution,
  onSignOut,
  onDeleteAccount,
}: {
  onBack: () => void;
  email: string | null;
  institutions: Institution[];
  onDeleteInstitution: (id: string) => Promise<void>;
  onSignOut: () => Promise<void>;
  onDeleteAccount: () => Promise<void>;
}) {
  const { appearance, setAppearance, useRoundedFont, setUseRoundedFont } =
    useTheme();
  const [haptics, setHapticsState] = useState(prefs.getHaptics());
  const [aboutOpen, setAboutOpen] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<Institution | null>(null);
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

        <SettingsSection title="Institutions">
          {institutions.length === 0 ? (
            <p className="py-2 text-sm text-on-surface-variant">
              No institutions yet.
            </p>
          ) : (
            institutions.map((inst, i) => (
              <div key={inst.id}>
                {i > 0 && <SettingsDivider />}
                <SettingsRow label={inst.name} sub={`${inst.type} · ${inst.currency}`}>
                  <button
                    aria-label={`Delete ${inst.name}`}
                    onClick={() => setPendingDelete(inst)}
                    className="rounded-full p-2 text-error transition hover:bg-error/10"
                  >
                    <Trash2 size={18} />
                  </button>
                </SettingsRow>
              </div>
            ))
          )}
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
        iconSrc="/icons/icon-512.png"
        links={[{ label: "Privacy policy", href: PRIVACY_URL }]}
      />

      <ConfirmDialog
        open={pendingDelete !== null}
        title="Delete institution?"
        message={`This permanently removes "${pendingDelete?.name}" and all of its entries.`}
        confirmLabel="Delete"
        danger
        busy={busy}
        onClose={() => setPendingDelete(null)}
        onConfirm={async () => {
          if (!pendingDelete) return;
          setBusy(true);
          try {
            await onDeleteInstitution(pendingDelete.id);
            setPendingDelete(null);
          } finally {
            setBusy(false);
          }
        }}
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
