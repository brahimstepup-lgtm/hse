import React from 'react';
import { useLanguage } from '../contexts/LanguageContext';

const Header: React.FC = () => {
  const { language, setLanguage, t } = useLanguage();

  const handleLanguageChange = (lang: 'ar' | 'fr') => {
    setLanguage(lang);
  };

  return (
    <header className="bg-white shadow-md">
      <div className="container mx-auto px-4 md:px-8 py-4 flex justify-between items-center">
        <div>
          <h1 className="text-2xl md:text-3xl font-bold text-sky-700">
            {t('headerTitle')}
          </h1>
          <p className="text-slate-500 mt-1">
            {t('headerSubtitle')}
          </p>
        </div>
        <div className="flex items-center gap-2">
           <span className="text-sm font-medium text-slate-600 hidden sm:inline">{t('language')}:</span>
            <button
              onClick={() => handleLanguageChange('ar')}
              className={`px-3 py-1 text-sm rounded-md transition ${language === 'ar' ? 'bg-sky-600 text-white font-bold' : 'bg-slate-200 text-slate-700 hover:bg-slate-300'}`}
              aria-pressed={language === 'ar'}
            >
              AR
            </button>
            <button
              onClick={() => handleLanguageChange('fr')}
              className={`px-3 py-1 text-sm rounded-md transition ${language === 'fr' ? 'bg-sky-600 text-white font-bold' : 'bg-slate-200 text-slate-700 hover:bg-slate-300'}`}
              aria-pressed={language === 'fr'}
            >
              FR
            </button>
        </div>
      </div>
    </header>
  );
};

export default Header;
