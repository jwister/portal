import { Button, Card, Empty, Input, Select, Skeleton, Tag, Typography } from '@douyinfe/semi-ui'
import { IconCopy, IconSearch } from '@douyinfe/semi-icons'
import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'

import '../../i18n'
import { getModelCatalog, type ModelCatalogItem } from '../../api/portal'

const { Title, Text } = Typography

export function ModelsPage() {
  const { t } = useTranslation()
  const [models, setModels] = useState<ModelCatalogItem[] | null>(null)
  const [query, setQuery] = useState('')
  const [vendor, setVendor] = useState('all')
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let active = true
    void getModelCatalog().then((items) => { if (active) setModels(items) }).catch(() => { if (active) setFailed(true) })
    return () => { active = false }
  }, [])

  const vendors = useMemo(() => models ? Array.from(new Set(models.map((item) => item.vendor))) : [], [models])
  const filtered = useMemo(() => (models ?? []).filter((item) => item.name.toLowerCase().includes(query.toLowerCase()) && (vendor === 'all' || item.vendor === vendor)), [models, query, vendor])

  if (failed) return <main className="models-page"><Empty description={t('models.error')} /></main>
  if (!models) return <main className="models-page"><Skeleton active placeholder={<Skeleton.Paragraph rows={12} />} /></main>

  return (
    <main className="models-page">
      <section className="models-hero"><Tag color="blue">{t('models.badge')}</Tag><Title heading={1}>{t('models.title')}</Title><Text type="tertiary">{t('models.copy', { count: models.length })}</Text></section>
      <section className="models-toolbar">
        <Input prefix={<IconSearch />} placeholder={t('models.search')} value={query} onChange={setQuery} showClear />
        <Select value={vendor} onChange={(value) => setVendor(String(value))} optionList={[{ value: 'all', label: t('models.allVendors') }, ...vendors.map((name) => ({ value: name, label: name }))]} />
        <Text type="tertiary">{t('models.count', { count: filtered.length })}</Text>
      </section>
      <section className="models-grid">
        {filtered.map((model) => <Card key={model.name} className="model-card" title={model.name} headerExtraContent={<Button theme="borderless" icon={<IconCopy />} aria-label={t('models.copyName')} onClick={() => navigator.clipboard?.writeText(model.name)} />}>
          <div className="model-prices">{model.priceAvailable ? <><span>{t('models.input')} <b>${model.inputPrice}</b></span><span>{t('models.output')} <b>${model.outputPrice}</b></span>{model.cachePrice !== null && <span>{t('models.cache')} <b>${model.cachePrice}</b></span>}</> : <Text type="tertiary">{t('models.priceUnavailable')}</Text>}</div>
          <div className="model-card-footer"><Tag size="small">{model.group}</Tag><Text type="tertiary">{model.vendor}</Text></div>
        </Card>)}
      </section>
    </main>
  )
}
