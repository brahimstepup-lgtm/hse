import React, { useState, useEffect } from 'react';
import Header from './components/Header';
import ReportForm from './components/ReportForm';
import ReportTable from './components/ReportTable';
import type { HseReport } from './types';
import { analyzeHseCase } from './services/geminiService';
import ErrorIcon from './components/icons/ErrorIcon';
import { useLanguage } from './contexts/LanguageContext';
import EditReportModal from './components/EditReportModal';
import ImagePreviewModal from './components/ImagePreviewModal';

const App: React.FC = () => {
  const [reports, setReports] = useState<HseReport[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const { language, t } = useLanguage();
  const [editingReport, setEditingReport] = useState<HseReport | null>(null);
  const [viewingImage, setViewingImage] = useState<string | null>(null);

  useEffect(() => {
    document.documentElement.lang = language;
    document.documentElement.dir = language === 'ar' ? 'rtl' : 'ltr';
  }, [language]);

  const handleGenerateReport = async (
    image: File,
    description: string,
    location: string
  ) => {
    setIsLoading(true);
    setError(null);
    try {
      const imageBase64 = await fileToBase64(image);
      const analysisResult = await analyzeHseCase(image, description, location);
      
      const newReport: HseReport = {
        id: `${Date.now()}-${Math.random()}`,
        date: new Date().toLocaleDateString(language === 'ar' ? 'ar-EG' : 'fr-FR', { year: 'numeric', month: 'long', day: 'numeric' }),
        location,
        image: imageBase64,
        ...analysisResult,
      };

      setReports(prevReports => [newReport, ...prevReports]);
    } catch (err) {
      setError(t('errorAnalysis'));
    } finally {
      setIsLoading(false);
    }
  };

  const fileToBase64 = (file: File): Promise<string> => {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.readAsDataURL(file);
      reader.onload = () => resolve(reader.result as string);
      reader.onerror = (error) => reject(error);
    });
  };

  const handleEditClick = (report: HseReport) => {
    setEditingReport(report);
  };
  
  const handleImageClick = (imageUrl: string) => {
    setViewingImage(imageUrl);
  };

  const handleCloseModal = () => {
    setEditingReport(null);
    setViewingImage(null);
  };

  const handleUpdateReport = (updatedReport: HseReport) => {
    setReports(reports.map(r => (r.id === updatedReport.id ? updatedReport : r)));
    setEditingReport(null);
  };

  return (
    <div className="bg-slate-50 min-h-screen font-sans text-slate-800">
      <Header />
      <main className="container mx-auto p-4 md:p-8">
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          <div className="lg:col-span-1">
            <ReportForm onSubmit={handleGenerateReport} isLoading={isLoading} />
          </div>
          <div className="lg:col-span-2">
            {error && (
              <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded-lg relative mb-6 flex items-center shadow-md">
                <ErrorIcon className="w-6 h-6 mx-3" />
                <span>{error}</span>
              </div>
            )}
            <ReportTable reports={reports} onEdit={handleEditClick} onImageClick={handleImageClick} />
          </div>
        </div>
      </main>
       {editingReport && (
        <EditReportModal
          report={editingReport}
          isOpen={!!editingReport}
          onClose={handleCloseModal}
          onSave={handleUpdateReport}
        />
      )}
      <ImagePreviewModal 
        isOpen={!!viewingImage}
        imageUrl={viewingImage || ''}
        onClose={handleCloseModal}
      />
    </div>
  );
};

export default App;