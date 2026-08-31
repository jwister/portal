import { Button, Card, Tag, Typography } from '@douyinfe/semi-ui'
import { useTranslation } from 'react-i18next'

import '../../i18n'

const { Title, Text } = Typography

export function HomePage() {
  const { t } = useTranslation()
  const features = [
    [t('home.featureOne'), t('home.featureOneCopy')],
    [t('home.featureTwo'), t('home.featureTwoCopy')],
    [t('home.featureThree'), t('home.featureThreeCopy')],
  ]
  const steps = [
    [t('home.stepOne'), t('home.stepOneCopy')],
    [t('home.stepTwo'), t('home.stepTwoCopy')],
    [t('home.stepThree'), t('home.stepThreeCopy')],
  ]

  return (
    <>
      <main className="semi-home">
        <section className="semi-hero" aria-labelledby="semi-hero-title">
          <div className="semi-hero-copy">
            <Tag color="blue" shape="circle">{t('home.badge')}</Tag>
            <Title heading={1} id="semi-hero-title">{t('home.title')}</Title>
            <Text className="semi-hero-description">{t('home.description')}</Text>
            <div className="semi-hero-actions"><a href="/sign-in"><Button type="primary" theme="solid" size="large">{t('home.start')}</Button></a><a href="/models"><Button theme="borderless" size="large">{t('home.models')}</Button></a></div>
            <div className="semi-compatibility"><Text type="tertiary">{t('home.compatibility')}</Text><span>OpenAI SDK</span><span>/v1</span></div>
          </div>
          <Card className="api-panel" shadows="always">
            <div className="api-panel-tabs"><span>Chat</span><span>Responses</span><span>Claude</span><strong>Gemini</strong></div>
            <code><b>POST</b> /v1/chat/completions</code>
            <pre>{`{
  "model": "gpt-5-mini",
  "messages": [{ "role": "user", "content": "Hello" }]
}`}</pre>
            <div className="api-panel-result">200 OK · 184 ms · stream ready</div>
          </Card>
        </section>
        <section className="home-metrics" aria-label={t('home.metricsLabel')}>
          {[['100+', t('home.metricModels')], ['40+', t('home.metricProviders')], ['/v1', t('home.metricCompatible')], ['24/7', t('home.metricAvailable')]].map(([value, label]) => <div key={String(label)}><strong>{value}</strong><span>{label}</span></div>)}
        </section>
        <section className="home-features"><div className="section-intro"><Tag color="green">{t('home.featureTag')}</Tag><Title heading={2}>{t('home.featureTitle')}</Title></div><div className="feature-grid">{features.map(([title, copy]) => <Card key={title} className="feature-card" title={title}><Text type="tertiary">{copy}</Text></Card>)}</div></section>
        <section className="onboarding-section"><div className="section-intro"><Tag color="blue">{t('home.stepsTag')}</Tag><Title heading={2}>{t('home.stepsTitle')}</Title></div><div className="onboarding-grid">{steps.map(([title, copy], index) => <Card key={title} className="step-card"><span>0{index + 1}</span><Title heading={4}>{title}</Title><Text type="tertiary">{copy}</Text></Card>)}</div></section>
        <section className="home-cta"><Title heading={2}>{t('home.ctaTitle')}</Title><Text>{t('home.ctaCopy')}</Text><a href="/sign-in"><Button type="primary" theme="solid" size="large">{t('home.ctaAction')}</Button></a></section>
      </main>
      <footer className="public-footer"><strong>Ztoken</strong><span>{t('home.footer')}</span></footer>
    </>
  )
}
