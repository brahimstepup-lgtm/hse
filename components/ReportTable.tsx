import React from 'react';
import type { HseReport, Severity } from '../types';
import { useLanguage } from '../contexts/LanguageContext';
import EditIcon from './icons/EditIcon';


interface ReportTableProps {
  reports: HseReport[];
  onEdit: (report: HseReport) => void;
  onImageClick: (imageUrl: string) => void;
}

const ReportTable: React.FC<ReportTableProps> = ({ reports, onEdit, onImageClick }) => {
  const { t, language } = useLanguage();

  const getSeverityStyles = (severity: Severity): string => {
    switch (severity) {
      case 'low':
        return 'bg-sky-100 text-sky-800';
      case 'medium':
        return 'bg-yellow-100 text-yellow-800';
      case 'high':
        return 'bg-orange-100 text-orange-800';
      case 'critical':
        return 'bg-red-100 text-red-800';
      default:
        return 'bg-slate-100 text-slate-800';
    }
  };

  if (reports.length === 0) {
    return (
      <div className="bg-white p-6 rounded-lg shadow-lg text-center text-slate-500">
        <h2 className="text-xl font-bold mb-2 text-slate-700">{t('tableTitle')}</h2>
        <p>{t('tableEmpty')}</p>
      </div>
    );
  }

  return (
    <div className="bg-white rounded-lg shadow-lg overflow-hidden">
      <h2 className="text-xl font-bold p-6 text-slate-700 border-b border-slate-200">{t('tableTitle')}</h2>
      <div className="overflow-x-auto">
        <table className={`w-full min-w-max text-sm text-slate-600 ${language === 'ar' ? 'text-right' : 'text-left'}`}>
          <thead className="text-xs text-slate-700 uppercase bg-slate-100">
            <tr>
              <th scope="col" className="px-6 py-3">{t('tableHeaderImage')}</th>
              <th scope="col" className="px-6 py-3">{t('tableHeaderDate')}</th>
              <th scope="col" className="px-6 py-3">{t('tableHeaderLocation')}</th>
              <th scope="col" className="px-6 py-3">{t('tableHeaderHazard')}</th>
              <th scope="col" className="px-6 py-3">{t('tableHeaderSeverity')}</th>
              <th scope="col" className="px-6 py-3">{t('tableHeaderSolution')}</th>
              <th scope="col" className="px-6 py-3">{t('tableHeaderResponsible')}</th>
              <th scope="col" className="px-6 py-3">{t('tableHeaderActions')}</th>
            </tr>
          </thead>
          <tbody>
            {reports.map((report) => (
              <tr key={report.id} className="bg-white border-b hover:bg-slate-50">
                <td className="px-6 py-4">
                  <button onClick={() => onImageClick(report.image)} className="focus:outline-none focus:ring-2 focus:ring-sky-500 rounded-md">
                    <img src={report.image} alt={`حالة ${report.location}`} className="w-16 h-16 object-cover rounded-md shadow-sm transition-transform hover:scale-110 cursor-pointer" />
                  </button>
                </td>
                <td className="px-6 py-4 font-medium whitespace-nowrap">{report.date}</td>
                <td className="px-6 py-4 whitespace-nowrap">{report.location}</td>
                <td className="px-6 py-4">
                  <span className="px-2 py-1 text-xs font-semibold text-amber-800 bg-amber-100 rounded-full">{report.hazardType[language]}</span>
                </td>
                <td className="px-6 py-4">
                  <span className={`px-2 py-1 font-semibold text-xs rounded-full ${getSeverityStyles(report.severity)}`}>
                    {t(`severity_${report.severity}` as any)}
                  </span>
                </td>
                <td className="px-6 py-4 min-w-[250px]">{report.proposedSolution[language]}</td>
                <td className="px-6 py-4 font-semibold text-sky-700 whitespace-nowrap">{report.responsiblePerson[language]}</td>
                <td className="px-6 py-4">
                  <button onClick={() => onEdit(report)} className="text-sky-600 hover:text-sky-800 font-medium flex items-center gap-1 p-1 rounded-md hover:bg-sky-100 transition-colors">
                    <EditIcon className="w-4 h-4" />
                    {t('actionEdit')}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default ReportTable;