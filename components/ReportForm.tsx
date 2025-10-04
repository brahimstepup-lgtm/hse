import React, { useState, useRef } from 'react';
import Spinner from './Spinner';
import CameraIcon from './icons/CameraIcon';
import AnalysisIcon from './icons/AnalysisIcon';
import { useLanguage } from '../contexts/LanguageContext';

interface ReportFormProps {
  onSubmit: (image: File, description: string, location: string) => void;
  isLoading: boolean;
}

const ReportForm: React.FC<ReportFormProps> = ({ onSubmit, isLoading }) => {
  const [image, setImage] = useState<File | null>(null);
  const [description, setDescription] = useState<string>('');
  const [location, setLocation] = useState<string>('');
  const [imagePreview, setImagePreview] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const { t } = useLanguage();

  const handleImageChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file) {
      if (file.size > 4 * 1024 * 1024) { // 4MB limit
        setError(t('formErrorImageSize'));
        return;
      }
      setError(null);
      setImage(file);
      const reader = new FileReader();
      reader.onloadend = () => {
        setImagePreview(reader.result as string);
      };
      reader.readAsDataURL(file);
    }
  };

  const triggerFileSelect = () => {
    fileInputRef.current?.click();
  };

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    if (!image || !description.trim() || !location.trim()) {
      setError(t('formErrorAllFields'));
      return;
    }
    setError(null);
    onSubmit(image, description, location);
  };

  return (
    <div className="bg-white p-6 rounded-lg shadow-lg sticky top-8">
      <h2 className="text-xl font-bold mb-4 text-slate-700">{t('formTitle')}</h2>
      <form onSubmit={handleSubmit} className="space-y-6">
        <div>
          <label className="block text-sm font-medium text-slate-600 mb-2">{t('formImageLabel')}</label>
          <input
            type="file"
            accept="image/*"
            onChange={handleImageChange}
            className="hidden"
            ref={fileInputRef}
            aria-label={t('formImageLabel')}
          />
          <div
            className="border-2 border-dashed border-slate-300 rounded-lg p-4 text-center cursor-pointer hover:border-sky-500 transition-colors"
            onClick={triggerFileSelect}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => e.key === 'Enter' && triggerFileSelect()}
          >
            {imagePreview ? (
              <img src={imagePreview} alt={t('formImageAlt')} className="max-h-48 w-auto mx-auto rounded-md" />
            ) : (
              <div className="flex flex-col items-center justify-center text-slate-500">
                <CameraIcon className="w-12 h-12 mb-2" />
                <span>{t('formImagePrompt')}</span>
                <span className="text-xs mt-1">{t('formImageMaxSize')}</span>
              </div>
            )}
          </div>
        </div>

        <div>
          <label htmlFor="description" className="block text-sm font-medium text-slate-600 mb-2">{t('formDescriptionLabel')}</label>
          <textarea
            id="description"
            rows={4}
            className="w-full px-3 py-2 border border-slate-300 rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-sky-500 focus:border-sky-500 transition"
            placeholder={t('formDescriptionPlaceholder')}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />
        </div>

        <div>
          <label htmlFor="location" className="block text-sm font-medium text-slate-600 mb-2">{t('formLocationLabel')}</label>
          <input
            type="text"
            id="location"
            className="w-full px-3 py-2 border border-slate-300 rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-sky-500 focus:border-sky-500 transition"
            placeholder={t('formLocationPlaceholder')}
            value={location}
            onChange={(e) => setLocation(e.target.value)}
          />
        </div>

        {error && <p className="text-red-600 text-sm" role="alert">{error}</p>}

        <button
          type="submit"
          disabled={isLoading}
          className="w-full flex items-center justify-center bg-sky-600 text-white font-bold py-3 px-4 rounded-lg hover:bg-sky-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-sky-500 transition-transform transform hover:scale-105 disabled:bg-slate-400 disabled:cursor-not-allowed disabled:scale-100"
        >
          {isLoading ? (
            <>
              <Spinner className="w-5 h-5 mx-2" />
              <span>{t('formSubmittingButton')}</span>
            </>
          ) : (
             <>
              <AnalysisIcon className="w-5 h-5 mx-2" />
              <span>{t('formSubmitButton')}</span>
            </>
          )}
        </button>
      </form>
    </div>
  );
};

export default ReportForm;
