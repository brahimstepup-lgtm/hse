import React from 'react';
import { useLanguage } from '../contexts/LanguageContext';
import CloseIcon from './icons/CloseIcon';

interface ImagePreviewModalProps {
  isOpen: boolean;
  imageUrl: string;
  onClose: () => void;
}

const ImagePreviewModal: React.FC<ImagePreviewModalProps> = ({ isOpen, imageUrl, onClose }) => {
  const { t } = useLanguage();
  if (!isOpen) return null;

  return (
    <div
      className="fixed inset-0 bg-black bg-opacity-75 z-50 flex justify-center items-center p-4 transition-opacity duration-300"
      onClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-label="Image Preview"
    >
      <div
        className="bg-white rounded-lg shadow-xl p-2 relative max-w-4xl max-h-[90vh] w-auto"
        onClick={e => e.stopPropagation()}
      >
        <button
          onClick={onClose}
          className="absolute top-2 right-2 md:-top-3 md:-right-3 bg-white rounded-full p-1.5 text-slate-700 hover:bg-slate-200 hover:text-slate-900 transition-all z-10"
          aria-label={t('imageModalClose')}
        >
          <CloseIcon className="w-6 h-6" />
        </button>
        <img
          src={imageUrl}
          alt="Enlarged report view"
          className="max-w-full max-h-[85vh] object-contain rounded"
        />
      </div>
    </div>
  );
};

export default ImagePreviewModal;
