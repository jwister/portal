import { Avatar, Button, Input, Select, Space, Toast, Typography } from '@douyinfe/semi-ui'
import { IconMail, IconUserCircle, IconUserSetting } from '@douyinfe/semi-icons'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'

import i18n from '../../i18n'
import { ConsolePageHeader } from '../../components/ConsolePageHeader'
import { RemoteState } from '../../components/RemoteState'
import { getProfile, updateProfile, type Profile } from '../../api/portal'

/** 从显示名或用户名取首字符，供账户身份卡展示且不引入额外头像数据。 */
function profileInitial(profile: Profile): string {
  return (profile.displayName || profile.username).trim().charAt(0).toUpperCase()
}

export function ProfilePage() {
  const { t } = useTranslation()
  const [profile, setProfile] = useState<Profile | null>(null)
  const [displayName, setDisplayName] = useState('')
  const [language, setLanguage] = useState('en')
  const [failed, setFailed] = useState(false)
  const [saving, setSaving] = useState(false)

  const load = () => {
    setFailed(false)
    setProfile(null)
    void getProfile().then((next) => {
      setProfile(next)
      setDisplayName(next.displayName)
      setLanguage(next.language ?? 'en')
    }).catch(() => setFailed(true))
  }

  useEffect(() => {
    load()
  }, [])

  const save = () => {
    if (!profile) return
    setSaving(true)
    void updateProfile({ displayName: displayName.trim(), language }).then((next) => {
      setProfile(next)
      setDisplayName(next.displayName)
      setLanguage(next.language ?? language)
      void i18n.changeLanguage(next.language ?? language)
      Toast.success(t('profile.saveSuccess'))
    }).catch(() => {
      Toast.error(t('profile.saveError'))
    }).finally(() => setSaving(false))
  }

  if (failed) return <RemoteState kind="error" onRetry={load} />
  if (!profile) return <RemoteState kind="loading" />

  return (
    <main>
      <ConsolePageHeader title={t('profile.title')} description={t('profile.description')} />
      <section className="console-identity-card" aria-labelledby="profile-identity-title">
        <Avatar size="extra-large" className="console-identity-avatar">{profileInitial(profile)}</Avatar>
        <div className="console-identity-copy">
          <Typography.Text id="profile-identity-title" strong>{t('profile.identity')}</Typography.Text>
          <Typography.Title heading={3}>{profile.displayName || profile.username}</Typography.Title>
          <span><IconUserCircle aria-hidden="true" />{profile.username}</span>
          <span><IconMail aria-hidden="true" />{profile.email}</span>
        </div>
      </section>
      <section className="profile-form" aria-labelledby="profile-preferences-title">
        <Typography.Title id="profile-preferences-title" heading={5}><IconUserSetting aria-hidden="true" /> {t('profile.preferences')}</Typography.Title>
        <label htmlFor="profile-username">{t('profile.username')}<Input id="profile-username" value={profile.username} disabled /></label>
        <Typography.Text type="tertiary">{t('profile.readOnly')}</Typography.Text>
        <label htmlFor="profile-email">{t('profile.email')}<Input id="profile-email" value={profile.email} disabled /></label>
        <Typography.Text type="tertiary">{t('profile.readOnly')}</Typography.Text>
        <label htmlFor="profile-display-name">{t('profile.displayName')}<Input id="profile-display-name" value={displayName} onChange={setDisplayName} /></label>
        <label>{t('profile.language')}<Select value={language} onChange={(value) => setLanguage(String(value))} optionList={[
          { label: t('profile.english'), value: 'en' },
          { label: t('profile.chinese'), value: 'zh-CN' },
        ]} /></label>
        <Space><Button theme="solid" type="primary" loading={saving} onClick={save}>{t('profile.save')}</Button></Space>
      </section>
    </main>
  )
}
