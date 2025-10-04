import React, { useState, useEffect } from 'react';
import type { HseReport } from '../types';
import { useLanguage } from '../contexts/LanguageContext';
import CloseIcon from './icons/CloseIcon';

interface EditReportModalProps {
  isOpen: boolean;
  report: HseReport;
  onClose: () => void;
  onSave: (report: HseReport) => void;
}

const EditReportModal: React.FC<EditReportModalProps> = ({ isOpen, report, onClose, onSave }) => {
  const [formData, setFormData] = useState<HseReport>(report);
  const { t, language } = useLanguage();

  useEffect(() => {
    setFormData(report);
  }, [report, isOpen]);

  if (!isOpen) return null;

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
    const { name, value } = e.target;

    if (name === 'hazardType' || name === 'proposedSolution' || name === 'responsiblePerson') {
      setFormData(prev => ({
        ...prev,
        [name]: {
          ...prev[name],
          [language]: value,
        },
      }));
    } else {
      setFormData(prev => ({ ...prev, [name]: value }));
    }
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onSave(formData);
  };

  return (
    <div 
      className="fixed inset-0 bg-black bg-opacity-50 z-50 flex justify-center items-center p-4" 
      onClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-labelledby="edit-modal-title"
    >
      <div className="bg-white rounded-lg shadow-xl p-6 md:p-8 w-11/12 md:w-2/3 lg:w-1/2 max-w-2xl relative" onClick={e => e.stopPropagation()}>
        <button
          onClick={onClose}
          className="absolute top-4 right-4 text-slate-500 hover:text-slate-800 transition-colors z-10"
          aria-label={t('editModalCancelButton')}
        >
          <CloseIcon className="w-6 h-6" />
        </button>
        <h2 id="edit-modal-title" className="text-2xl font-bold mb-6 text-slate-800">{t('editModalTitle')}</h2>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="location" className="block text-sm font-medium text-slate-600 mb-1">{t('editModalLocationLabel')}</label>
            <input type="text" name="location" id="location" value={formData.location} onChange={handleChange} className="w-full px-3 py-2 border border-slate-300 rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-sky-500"/>
          </div>
          <div>
            <label htmlFor="hazardType" className="block text-sm font-medium text-slate-600 mb-1">{t('editModalHazardLabel')}</label>
            <input type="text" name="hazardType" id="hazardType" value={formData.hazardType[language]} onChange={handleChange} className="w-full px-3 py-2 border border-slate-300 rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-sky-500"/>
          </div>
          <div>
            <label htmlFor="severity" className="block text-sm font-medium text-slate-600 mb-1">{t('editModalSeverityLabel')}</label>
             <select name="severity" id="severity" value={formData.severity} onChange={handleChange} className="w-full px-3 py-2 border border-slate-300 rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-sky-500 bg-white">
                <option value="low">{t('severity_low')}</option>
                <option value="medium">{t('severity_medium')}</option>
                <option value="high">{t('severity_high')}</option>
                <option value="critical">{t('severity_critical')}</option>
            </select>
          </div>
          <div>
            <label htmlFor="proposedSolution" className="block text-sm font-medium text-slate-600 mb-1">{t('editModalSolutionLabel')}</label>
            <textarea name="proposedSolution" id="proposedSolution" rows={3} value={formData.proposedSolution[language]} onChange={handleChange} className="w-full px-3 py-2 border border-slate-300 rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-sky-500"/>
          </div>
          <div>
            <label htmlFor="responsiblePerson" className="block text-sm font-medium text-slate-600 mb-1">{t('editModalResponsibleLabel')}</label>
            <input type="text" name="responsiblePerson" id="responsiblePerson" value={formData.responsiblePerson[language]} onChange={handleChange} className="w-full px-3 py-2 border border-slate-300 rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-sky-500"/>
          </div>
          <div className="flex justify-end items-center gap-4 pt-4">
            <button type="button" onClick={onClose} className="px-4 py-2 bg-slate-100 text-slate-700 rounded-lg hover:bg-slate-200 transition">{t('editModalCancelButton')}</button>
            <button type="submit" className="px-6 py-2 bg-sky-600 text-white font-bold rounded-lg hover:bg-sky-700 transition">{t('editModalSaveButton')}</button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default EditReportModal;